
/*
 * The entry point into the connector from the Vaadin framework.
 */
org_mpilone_vaadin_upload_fineuploader_FineUploader = function() {

  var BROWSE_BUTTON_CAPTION = "Choose File";
  var BUTTON_CLASSNAME = "v-button v-widget";
  var BROWSE_BUTTON_CLASSNAME = "fineuploader-browse " + BUTTON_CLASSNAME;
  var SUBMIT_BUTTON_CLASSNAME = "fineuploader-submit " + BUTTON_CLASSNAME;
  var DEFAULT_STREAMING_PROGRESS_EVENT_INTERVAL_MS = 3000;
  var DISPLAY_NONE = "none";

  /*
   *  The root HTML element that represents this component. 
   */
  var element = this.getElement();

  /*
   * The RPC proxy to the server side implementation.
   */
  var rpcProxy = this.getRpcProxy();

  /*
   * The unique ID of the connector.
   */
  var connectorId = this.getConnectorId();

  /*
   * The uploader currently displayed.
   */
  var uploader;

  /**
   * The div that contains the buttons and inputs for upload.
   * 
   * @type @exp;document@call;createElement
   */
  var container;

  /**
   * The div that acts as the browse for files button.
   * 
   * @type @exp;document@call;createElement
   */
  var browseBtn;

  /**
   * The div that acts as the submit button when in manual mode.
   * 
   * @type @exp;document@call;createElement
   */
  var submitBtn;

  /**
   * The input that displays the file name in manual mode.
   * 
   * @type @exp;document@call;createElement
   */
  var fileInput;

  /**
   * The last time a progress RPC call was sent to the server side. This is 
   * used to throttle the progress calls to prevent flooding the server side.
   * 
   * @type Number
   */
  var lastProgressRpc = 0;

  /*
   * Simple method for logging to the JS console if one is available.
   */
  function console_log(msg) {
    if (window.console) {
      console.log(msg);
    }
  }

  /**
   * Builds the container divs and the buttons in the div.
   * 
   * @param {type} state
   * @returns {undefined}
   */
  this._buildButtons = function(state) {

    // Container
    container = document.createElement("div");
    container.setAttribute("id", "fineuploader_container_" + connectorId);
    container.className = "fineuploader";
    element.appendChild(container);

    // Browse button.
    browseBtn = this._createPseudoVaadinButton();
    browseBtn.root.className = BROWSE_BUTTON_CLASSNAME;
    browseBtn.caption.innerHTML = BROWSE_BUTTON_CAPTION;
    container.appendChild(browseBtn.root);

    browseBtn.disabledBtn = this._createPseudoVaadinButton();
    browseBtn.disabledBtn.root.className = BROWSE_BUTTON_CLASSNAME + " v-disabled";
    browseBtn.disabledBtn.root.style.display = DISPLAY_NONE;
    browseBtn.disabledBtn.caption.innerHTML = BROWSE_BUTTON_CAPTION;
    container.appendChild(browseBtn.disabledBtn.root);

    // If immediate, apply the button caption to the browse button.
    if (state.immediateMode && state.buttonCaption) {
      browseBtn.caption.innerHTML = state.buttonCaption;
      browseBtn.disabledBtn.caption.innerHTML = state.buttonCaption;
    }
    // If not immediate, add the file input box to populate with the selected file.
    else if (!state.immediateMode) {
      fileInput = document.createElement("input");
      fileInput.setAttribute("type", "text");
      fileInput.setAttribute("readonly", "true");
      fileInput.className = "fineuploader-file v-textfield v-widget v-textfield-prompt v-readonly v-textfield-readonly";
      container.appendChild(fileInput);

      // If there is a button caption, create a separate submit button 
      // otherwise assume it will be submitted on the server side.
      if (state.buttonCaption) {
        submitBtn = this._createPseudoVaadinButton();
        submitBtn.root.className = SUBMIT_BUTTON_CLASSNAME;
        submitBtn.caption.innerHTML = state.buttonCaption;
        submitBtn.root.onclick = function() {
          if (uploader.getUploads().length > 0
                  && uploader.getInProgress() === 0) {
            uploader.uploadStoredFiles();
          }
        };
        container.appendChild(submitBtn.root);

        submitBtn.disabledBtn = this._createPseudoVaadinButton();
        submitBtn.disabledBtn.root.className = SUBMIT_BUTTON_CLASSNAME + " v-disabled";
        submitBtn.disabledBtn.caption.innerHTML = state.buttonCaption;
        submitBtn.disabledBtn.root.style.display = DISPLAY_NONE;
        container.appendChild(submitBtn.disabledBtn.root);
      }
    }
  };

  /**
   * Builds the uploader.
   * 
   * @param {type} state
   * @returns {undefined}
   */
  this._buildUploader = function(state) {

    // Create the uploader.
    uploader = new qq.FineUploaderBasic({
      autoUpload: state.immediateMode,
      button: browseBtn.root,
      callbacks: {
        onUpload: function(id, name) {
          console_log("onUpload: " + name);
          lastProgressRpc = 0;
        },
        onComplete: function(id, name, responseJSON, xhr) {
          console_log("onComplete: " + name);
          rpcProxy.onComplete(id, name);

          uploader.clearStoredFiles();
        },
        onError: function(id, name, errorReason, xhr) {
          console_log("onError: " + name);
          if (uploader.getInProgress() === 0) {
            rpcProxy.onError(id, name, errorReason);
          }
        },
        onProgress: function(id, name, uploadBytes, totalBytes) {
          console_log("onProgress: " + name);

          var now = new Date().getTime();
          if (lastProgressRpc + DEFAULT_STREAMING_PROGRESS_EVENT_INTERVAL_MS <= now) {
            lastProgressRpc = now;
            rpcProxy.onProgress(id, name, uploadBytes, totalBytes);
          }
        },
        onStatusChange: function(id, oldStatus, newStatus) {
          console_log("onStatusChange: " + oldStatus + ", " + newStatus);
        },
        onSubmit: function(id, name) {
          // Clear the current items.
          uploader.clearStoredFiles();

          // Update the file input display.
          if (fileInput !== null) {
            fileInput.value = name;
          }
          return true;
        }
      },
      chunking: {
        enabled: (state.chunkSize > 0),
        partSize: state.chunkSize
      },
      debug: true,
      request: {
        endpoint: this.translateVaadinUri(state.url)
      },
      retry: {
        enableAuto: (state.maxRetries > 0),
        maxAutoAttempts: state.maxRetries
      },
      validation: {
        sizeLimit: state.maxFileSize
      }
    });

    rpcProxy.onInit("doing fine (" + connectorId + ")");
  };

  /**
   * Swaps the style.display property between the two divs.
   * 
   * @param {type} src div element
   * @param {type} dest div element
   * @returns {undefined}
   */
  this._swapDisplayStyle = function(src, dest) {
    var tmp = src.style.display;

    src.style.display = dest.style.display;
    dest.style.display = tmp;
  };

  /**
   * Called when the component is being unregistered (i.e. removed) from the UI. 
   * Cancel an in-progress uploads and destroy the uploader.
   * 
   * @returns {undefined}
   */
  this.onUnregister = function() {
    if (uploader) {
      console_log("Cancelling and cleaning up uploader component.");

      try {
        uploader.cancelAll();
      }
      catch (ex) {
        // no op
      }
      uploader = null;
    }
  };

  /*
   * Called when the state on the server side changes. If the state 
   * changes require a rebuild of the upload component, it will be 
   * destroyed and recreated. All other state changes will be applied 
   * to the existing upload instance.
   */
  this.onStateChange = function() {

    var state = this.getState();

    console_log("State change!");

    if (!uploader || state.rebuild) {
      element.innerHTML = "";
      uploader = null;

      console_log("Building uploader for connector " + connectorId);
      this._buildButtons(state);
      this._buildUploader(state);
    }

    // Check for browse enabled and update the button visibility accordingly.
    if ((state.enabled && browseBtn.root.style.display === DISPLAY_NONE) ||
            (!state.enabled && browseBtn.disabledBtn.root.style.display === DISPLAY_NONE)) {
      this._swapDisplayStyle(browseBtn.root, browseBtn.disabledBtn.root);

      if (submitBtn !== null) {
        this._swapDisplayStyle(submitBtn.root, submitBtn.disabledBtn.root);
      }
    }


  };

  /**
   * Submits the upload if there is a file selected.
   * 
   * @returns {undefined}
   */
  this.submitUpload = function() {
    if (uploader.getUploads().length > 0 && uploader.getInProgress() === 0) {
      console_log("Starting upload due to server side submit.");
      uploader.uploadStoredFiles();
    }
  };

  this._createPseudoVaadinButton = function() {

    var btn = document.createElement("div");
    btn.setAttribute("role", "button");
    btn.className = BUTTON_CLASSNAME;

    var btnWrap = document.createElement("span");
    btnWrap.className = "v-button-wrap";
    btn.appendChild(btnWrap);

    var btnCaption = document.createElement("span");
    btnCaption.className = "v-button-caption";
    btnCaption.innerHTML = "Button";
    btnWrap.appendChild(btnCaption);

    return {
      root: btn,
      wrap: btnWrap,
      caption: btnCaption
    };

  };

  // -----------------------
  // Init component
  this.registerRpc("org.mpilone.vaadin.upload.fineuploader.shared.FineUploaderClientRpc", this);
};