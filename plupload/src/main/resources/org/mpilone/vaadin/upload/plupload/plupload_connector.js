
/*
 * The entry point into the connector from the Vaadin framework.
 */
org_mpilone_vaadin_upload_plupload_Plupload = function() {

  var BROWSE_BUTTON_CAPTION = "Choose File";
  var BUTTON_CLASSNAME = "v-button v-widget";
  var BROWSE_BUTTON_CLASSNAME = "plupload-browse " + BUTTON_CLASSNAME;
  var SUBMIT_BUTTON_CLASSNAME = "plupload-submit " + BUTTON_CLASSNAME;
  var DEFAULT_STREAMING_PROGRESS_EVENT_INTERVAL_MS = 3000;

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

  /*
   * The flag which indicates if the upload should be immidiate after 
   * file selection.
   */
  var immediate = false;

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
    container.setAttribute("id", "plupload_container_" + connectorId);
    container.className = "plupload";
    element.appendChild(container);

    // Browse button.
    browseBtn = this._createPseudoVaadinButton();
    browseBtn.root.className = BROWSE_BUTTON_CLASSNAME;
    browseBtn.caption.innerHTML = BROWSE_BUTTON_CAPTION;
    browseBtn.enabled = true;
    container.appendChild(browseBtn.root);

    // If immediate, apply the button caption to the browse button.
    if (state.immediateMode && state.buttonCaption) {
      browseBtn.caption.innerHTML = state.buttonCaption;
    }
    // If not immediate, add the file input box to populate with the selected file.
    else if (!state.immediateMode) {
      fileInput = document.createElement("input");
      fileInput.setAttribute("type", "text");
      fileInput.setAttribute("readonly", "true");
      fileInput.className = "plupload-file v-textfield v-widget v-textfield-prompt v-readonly v-textfield-readonly";
      container.appendChild(fileInput);

      // If there is a button caption, create a separate submit button 
      // otherwise assume it will be submitted on the server side.
      if (state.buttonCaption) {
        submitBtn = this._createPseudoVaadinButton();
        submitBtn.root.className = SUBMIT_BUTTON_CLASSNAME;
        submitBtn.caption.innerHTML = state.buttonCaption;
        submitBtn.root.onclick = function() {
          if (uploader.files && uploader.files.length > 0) {
            uploader.start();
          }
        };
        container.appendChild(submitBtn.root);
      }
    }
  };

  /*
   * Builds and returns a Plupload uploader component using 
   * the given state information.
   */
  this._buildUploader = function(state) {

    var uploadUrl = this.translateVaadinUri(state.url);
    var flashSwfUrl = this.translateVaadinUri(state.resources["flashUrl"].uRL);
    var silverlightXapUrl = this.translateVaadinUri(state.resources["silverlightUrl"].uRL);

    uploader = new plupload.Uploader({
      runtimes: state.runtimes,
      browse_button: browseBtn.root,
      container: container,
      max_file_size: state.maxFileSize,
      chunk_size: state.chunkSize,
      max_retries: state.maxRetries,
      multi_selection: false,
      url: uploadUrl,
      flash_swf_url: flashSwfUrl,
      silverlight_xap_url: silverlightXapUrl
    });

    uploader.bind('UploadFile', function(up, file) {
      console_log("Upload file: " + file.name + " with size " + file.size);

      lastProgressRpc = 0;

      // It appears that size may be null for HTML4 upload in IE8.
      var size = file.size ? file.size : -1;

      rpcProxy.onUploadFile(file.id, file.name, size);
    });

    uploader.bind('Error', function(up, error) {
      var output = '';
      for (property in error) {
        output += property + ': ' + error[property] + '; ';
      }
      console_log(output);

      var id = error.file ? error.file.id : null;
      var name = error.file ? error.file.name : null;
      var size = error.file ? error.file.size : -1;
      var type = error.file ? error.file.type : null;

      // It appears that size may be null for HTML4 upload in IE8.
      size = size ? size : -1;

      rpcProxy.onError(id, name, type, size, error.code, error.message);
    });

    uploader.bind('FilesAdded', function(up, files) {
      
      // We only want the last file selected so we remove any other files 
      // already in the queue.
      var numFiles = files.length;
      var file = files[numFiles - 1];
      for (var i = 0; i < numFiles - 1; i++) {
        up.removeFile(files[i]);
      }
      
      console_log("Files added: " + file.name);
      if (fileInput) {
        fileInput.value = file.name;
      }

      if (immediate && uploader.state === plupload.STOPPED) {
        console_log("Starting immediately.");
        window.setTimeout(function() {
          uploader.start();
        }, 200);
      }
    });

    uploader.bind('ChunkUploaded', function(up, file, chunkResponse) {
      var response = JSON.parse(chunkResponse.response);

      console_log("Chunk complete. Response: " + chunkResponse.response);

      if (response.preventRetry) {
        console_log("Preventing retries after chunk response.");
        uploader.stop();
      }
    });

    uploader.bind('UploadComplete', function(up, files) {
      console_log("Upload is complete");

      // Clear the queue.
      uploader.splice(0, files.length);
    });

    uploader.bind('StateChanged', function(up) {
      console_log("StateChanged: " + up.state);
      rpcProxy.onStateChanged(up.state);
    });

    uploader.bind('FileUploaded', function(up, file) {
      console_log("FileUploaded: " + file.name);

      var size = file.size ? file.size : -1;
      rpcProxy.onFileUploaded(file.id, file.name, size);
    });

    uploader.bind('Init', function(up) {
      console_log("Init: " + up.runtime);
      rpcProxy.onInit(up.runtime);
    });

    uploader.bind('PostInit', function(up) {
      //console_log("PostInit: " + up.runtime);
    });

    uploader.bind('UploadProgress', function(up, file) {
      // Throttle the progress events so we don't flood the RPC channel.
      var now = new Date().getTime();
      if (lastProgressRpc + DEFAULT_STREAMING_PROGRESS_EVENT_INTERVAL_MS <= now) {
        console_log("UploadProgress: " + file.percent);
        lastProgressRpc = now;
        rpcProxy.onProgress(file.id, file.name, file.loaded, file.size);
      }
    });

    uploader.bind('FilesRemoved', function(up, files) {
      console_log("Files removed: " + files[0].name);
      if (fileInput) {
        fileInput.value = "";
      }
    });

    uploader.init();
  };

  /**
   * Called when the component is being unregistered (i.e. removed) from the UI. 
   * Cancel an in-progress uploads and destroy the uploader.
   * 
   * @returns {undefined}
   */
  this.onUnregister = function() {
    if (uploader) {
      console_log("Stopping and cleaning up uploader component.");

      try {
        uploader.stop();
        uploader.destroy();
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
      console_log("Building uploader for connector " + connectorId);

      // Cleanup the current uploader if there is one.
      if (uploader) {
        uploader.destroy();
      }
      uploader = null;
      element.innerHTML = "";

      try {
        // Build the new uploader.
        this._buildButtons(state);
        this._buildUploader(state);

        immediate = state.immediateMode;
      }
      catch (ex) {
        // TODO: This needs to be cleaned up!
        console_log(ex);
        alert(ex);
      }
    }

    // Apply state changes that don't require a rebuild.
    if (uploader.getOption("chunk_size") !== state.chunkSize) {
      uploader.setOption("chunk_size", state.chunkSize);
    }
    if (uploader.getOption("max_file_size") !== state.maxFileSize) {
      uploader.setOption("max_file_size", state.maxFileSize);
    }
    if (uploader.getOption("max_retries") !== state.maxRetries) {
      uploader.setOption("max_retries", state.maxRetries);
    }

    // Check for browse enabled and update the button visibility accordingly.
    // Vaadin will not send default values and because of the way JS
    // components work default values will not appear in the state.
    var stateEnabled = true;
    if (state.hasOwnProperty['enabled']) {
    	stateEnabled = state.enabled;
    }
    
    if (stateEnabled && !browseBtn.enabled) {
      uploader.disableBrowse(false);
      browseBtn.root.className = BROWSE_BUTTON_CLASSNAME;
      browseBtn.enabled = true;

      if (submitBtn) {
        submitBtn.root.className = SUBMIT_BUTTON_CLASSNAME;
      }
    }
    else if (!stateEnabled && browseBtn.enabled) {
      uploader.disableBrowse(true);
      browseBtn.root.className = BROWSE_BUTTON_CLASSNAME + " v-disabled";
      browseBtn.enabled = false;

      if (submitBtn) {
        submitBtn.root.className = SUBMIT_BUTTON_CLASSNAME + " v-disabled";
      }
    }
  };

  /**
   * Submits the upload if there is a file selected.
   * 
   * @returns {undefined}
   */
  this.submitUpload = function() {
    if (uploader.state === plupload.STOPPED
            && uploader.files && uploader.files.length > 0) {
      console_log("Starting upload due to server side submit.");
      uploader.start();
    }
  };
  
  /**
   * Interrupts the upload if there is a file in progress.
   * 
   * @returns {undefined}
   */
  this.interruptUpload = function() {
    // Check for upload stop state change.
    if (uploader.state === plupload.STARTED) {
      console_log("Interrupting upload.");
      uploader.stop();
      
      // We have to generate a call to finalize the upload on the server side
      // because a manual stop of Plupload won't generate any event.
      rpcProxy.onError("", "", "", -1, null, "interrupted server side");
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
  this.registerRpc("org.mpilone.vaadin.upload.plupload.shared.PluploadClientRpc", this);
};