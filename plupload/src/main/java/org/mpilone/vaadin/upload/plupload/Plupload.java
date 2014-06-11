package org.mpilone.vaadin.upload.plupload;

import static org.mpilone.vaadin.upload.Streams.removePath;
import static org.mpilone.vaadin.upload.Streams.tryClose;

import java.io.*;

import org.mpilone.vaadin.upload.*;
import org.mpilone.vaadin.upload.plupload.shared.*;
import org.slf4j.*;

import com.vaadin.annotations.JavaScript;
import com.vaadin.server.*;
import com.vaadin.server.communication.FileUploadHandler;
import com.vaadin.ui.Upload;
import com.vaadin.util.FileTypeResolver;

/**
 * <p>
 * Wrapper for the Plupload HTML5/Flash/HTML4 upload component. You can find
 * more information at http://www.plupload.com/. This implementation attempts to
 * follow the {@link Upload} API as much as possible to be a drop-in
 * replacement.
 * </p>
 *
 * @author mpilone
 */
@JavaScript({"plupload_connector.js", "plupload/js/plupload.full.min.js"})
public class Plupload extends AbstractHtml5Upload {

  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = 1L;

  /**
   * The log for this class.
   */
  private final Logger log = LoggerFactory.getLogger(getClass());

  private final PluploadServerRpc serverRpc = new ServerRpcImpl();
  private final PluploadClientRpc clientRpc;

  private StreamVariable streamVariable;
  private Runtime runtime;

  private UploadSession uploadSession;

  /**
   * Constructs the upload component.
   *
   * @see #Plupload(java.lang.String, com.vaadin.ui.Upload.Receiver)
   */
  public Plupload() {
    this(null, null);
  }

  /**
   * Constructs the upload component. The following defaults will be used:
   * <ul>
   * <li>runtimes: html5,flash,html4</li>
   * <li>chunkSize: 0 (i.e. disabled)</li>
   * <li>maxFileSize: 10MB</li>
   * <li>maxRetries: 0 (i.e. disabled)</li>
   * </ul>
   *
   * @param caption the caption of the component
   * @param receiver the receiver to create the output stream to receive upload
   * data
   */
  public Plupload(String caption, Upload.Receiver receiver) {
    registerRpc(serverRpc);
    clientRpc = getRpcProxy(PluploadClientRpc.class);

    // Add the Silverlight mime-type if it isn't already in the resolver.
    if (FileTypeResolver.DEFAULT_MIME_TYPE.equals(FileTypeResolver.getMIMEType(
        "Moxie.xap"))) {
      FileTypeResolver.addExtension("xap", "application/x-silverlight-app");
    }

    setResource("flashUrl", new ClassResource(getClass(),
        "plupload/js/Moxie.swf"));
    setResource("silverlightUrl", new ClassResource(getClass(),
        "plupload/js/Moxie.xap"));

    setCaption(caption);
    setRuntimes(Runtime.HTML5, Runtime.FLASH, Runtime.HTML4);
    setReceiver(receiver);
    setMaxFileSize(10 * 1024 * 1024);
  }

  @Override
  public void attach() {
    super.attach();

    // Generate the URL using the standard FileUploadHandler format and then
    // replace the URL prefix with the prefix for our custom upload request
    // handler. This ensures that the IDs and security key are properly
    // generated and registered for our stream variable.
    String url = getSession().getCommunicationManager().
        getStreamVariableTargetUrl(this, "plupload", getStreamVariable());

    // Replace the default upload URL prefix with the advanced upload request
    // handler to be sure advanced stream variable events are generated.
    url = url.replace(ServletPortletHelper.UPLOAD_URL_PREFIX,
        Html5FileUploadHandler.URL_PREFIX);

    getState().url = url;
    getState().rebuild = true;

    installHandler();
  }

  @Override
  public void detach() {
    // Cleanup our stream variable.
    getUI().getConnectorTracker().cleanStreamVariable(getConnectorId(),
        "plupload");

    super.detach();
  }

  @Override
  public void setVisible(boolean visible) {
    super.setVisible(visible); //To change body of generated methods, choose Tools | Templates.
  }

  /**
   * Returns the stream variable that will receive the data events and content.
   *
   * @return the stream variable for this component
   */
  public StreamVariable getStreamVariable() {
    if (streamVariable == null) {
      streamVariable = new StreamVariableImpl();
    }

    return streamVariable;
  }

  /**
   * Returns the caption displayed on the submit button or on the combination
   * browse and submit button when in immediate mode.
   *
   * @return the caption of the submit button
   */
  public String getButtonCaption() {
    return getState().buttonCaption;
  }

  /**
   * Sets the caption displayed on the submit button or on the combination
   * browse and submit button when in immediate mode. When not in immediate
   * mode, the text on the browse button cannot be set.
   *
   * @param caption the caption of the submit button
   */
  public void setButtonCaption(String caption) {
    getState().buttonCaption = caption;
    getState().rebuild = true;
  }

  /**
   * Returns the maximum number of retries if an upload fails.
   *
   * @return the number of retries
   */
  public long getMaxRetries() {
    return getState().maxRetries;
  }

  /**
   * Sets the maximum number of retries if an upload fails. Plupload resets the
   * retry count per chunk, not for the entire upload.
   *
   * @param maxRetries the number of retries
   */
  public void setMaxRetries(int maxRetries) {
    getState().maxRetries = maxRetries;
  }

  /**
   * Returns the number of bytes read since the upload started. This value is
   * cleared after a successful upload.
   *
   * @return the number of bytes read
   */
  public long getBytesRead() {
    return uploadSession.bytesRead;
  }

  /**
   * Sets the size in bytes of each data chunk to be sent from the client to the
   * server. Not all runtimes support chunking. If set to 0, chunking will be
   * disabled.
   *
   * @param size the size of each data chunk
   */
  public void setChunkSize(int size) {
    getState().chunkSize = size;
  }

  /**
   * Sets the size in bytes of each data chunk to be sent from the client to the
   * server.
   *
   * @return the size of each data chunk
   */
  public int getChunkSize() {
    return getState().chunkSize;
  }

  /**
   * Sets the maximum size in bytes of files that may be selected and uploaded.
   *
   * @param size the maximum file size that may be uploaded
   */
  public void setMaxFileSize(long size) {
    getState().maxFileSize = size;
  }

  /**
   * Starts the upload of any files in the upload queue. Once started, the
   * uploads cannot be stopped until an error occurs or all the data is received
   * (this may change in the future).
   */
  public void submitUpload() {
    if (uploadSession != null) {
      throw new IllegalStateException("Uploading in progress.");
    }

    clientRpc.submitUpload();
  }

  /**
   * Returns the size (i.e. reported content length) of the current upload. This
   * value may not be known and will be cleared after a successful upload.
   *
   * @return the upload size in bytes
   */
  public long getUploadSize() {
    return uploadSession == null ? -1 : uploadSession.contentLength;
  }

  /**
   * Interrupts the upload currently being received. The interruption will be
   * done by the receiving thread so this method will return immediately and the
   * actual interrupt will happen a bit later.
   */
  public void interruptUpload() {
    if (uploadSession != null) {
      uploadSession.interrupted = true;
      clientRpc.interruptUpload();
    }
  }

  /**
   * Go into upload state. Due to buffering of RPC calls by Vaadin, it is
   * possible that the upload could be started by the data stream or the RPC
   * call. It is safe to call this method multiple times and additional calls
   * will simply be ignored.
   *
   * Warning: this is an internal method used by the framework and should not be
   * used by user of the Upload component. Using it results in the Upload
   * component going in wrong state and not working. It is currently public
   * because it is used by another class.
   */
  private void startUpload() {
    if (uploadSession == null) {
      uploadSession = new UploadSession();
    }

//    getState().interruptUpload = false;
  }

  /**
   * Go into state where new uploading can begin.
   *
   * Warning: this is an internal method used by the framework and should not be
   * used by user of the Upload component.
   */
  private void endUpload() {
    // Cleanup the receiver stream.
    if (uploadSession != null) {
      if (uploadSession.receiverOutstream != null) {
        tryClose(uploadSession.receiverOutstream);
      }

      if (uploadSession.succeededEventPending) {
        fireUploadSuccess(new SucceededEvent(Plupload.this,
            uploadSession.filename, uploadSession.mimeType,
            uploadSession.bytesRead));
      }
      else if (uploadSession.exception instanceof NoInputStreamException) {
        fireNoInputStream(uploadSession.filename,
            uploadSession.mimeType, uploadSession.contentLength);
      }
      else if (uploadSession.exception instanceof NoOutputStreamException) {
        fireNoOutputStream(uploadSession.filename,
            uploadSession.mimeType, uploadSession.contentLength);
      }
      else {
        fireUploadInterrupted(new FailedEvent(Plupload.this,
            uploadSession.filename, uploadSession.mimeType,
            uploadSession.contentLength, uploadSession.exception));
      }

      uploadSession = null;
    }
  }

  /**
   * Returns true if an upload is currently in progress.
   *
   * @return the upload in progress
   */
  public boolean isUploading() {
    return uploadSession != null;
  }

  /**
   * Sets the list of runtimes that the uploader will attempt to use. It will
   * try to initialize each runtime in order if one fails it will move on to the
   * next one.
   *
   * @param runtimes the list of runtimes
   */
  public void setRuntimes(Runtime... runtimes) {
    String value = "";

    for (Runtime r : runtimes) {
      if (!value.isEmpty()) {
        value += ",";
      }
      value += r.name().toLowerCase();
    }

    getState().runtimes = value;
    getState().rebuild = true;
  }

  /**
   * Returns the list of runtimes that the uploader will attempt to use.
   *
   * @return the list of runtimes
   */
  public Runtime[] getRuntimes() {
    String[] runtimes = new String[0];
    if (getState().runtimes != null) {
      runtimes = getState().runtimes.split(",");
    }

    int i = 0;
    Runtime[] values = new Runtime[runtimes.length];
    for (String r : runtimes) {
      values[i++] = Runtime.valueOf(r.toUpperCase());
    }

    return values;
  }

  /**
   * Returns the runtime selected on the client side after initialization. This
   * method will return null until the runtime is selected on the client side.
   *
   * @return the runtime selected by the framework on the client side based on
   * browser and plug-in capabilities
   */
  public Runtime getSelectedRuntime() {
    return runtime;
  }

  @Override
  protected PluploadState getState() {
    return (PluploadState) super.getState();
  }

  /**
   * The remote procedure call interface which allows calls from the client side
   * to the server. For the most part these methods map to the events generated
   * by the Plupload JavaScript component.
   */
  private class ServerRpcImpl implements PluploadServerRpc {

    /**
     * Serialization ID.
     */
    private static final long serialVersionUID = 1L;

    @Override
    public void onError(String id, String name, String contentType,
        int contentLength, Integer errorCode, String errorReason) {

      log.info("Error on upload. id: {}, name: {}, reason: {}", id, name,
          errorReason);

      if (errorCode != null && errorCode.equals(ErrorCode.FILE_SIZE_ERROR.
          getCode())) {
        fireFileSizeExceeded(new FileSizeExceededEvent(Plupload.this, name,
            contentType, contentLength));
      }

      endUpload();
    }

    @Override
    public void onStateChanged(int state) {
      // no op. Might remove in the future.
    }

    @Override
    public void onUploadFile(String id, String name, int contentLength) {

      if (contentLength > 0 && uploadSession != null
          && uploadSession.contentLength != contentLength) {
        // Get the more accurate content length from the file
        // if the upload has already started.
        uploadSession.contentLength = contentLength;
      }
    }

    @Override
    public void onFileUploaded(String id, String name, int contentLength) {
      
      // End the upload if there was one in progress.
      endUpload();
    }

    @Override
    public void onProgress(String id, String name, int uploadedBytes,
        int totalBytes) {
      // We want the call to refresh uploader state (i.e. polling)
      // but we don't care about the progress value.

      // Update the content length value as it may be more accurate than
      // what we have from the actual POST data.
      if (totalBytes > 0 && uploadSession != null
          && uploadSession.contentLength != totalBytes) {

        // Get the more accurate content length from the file
        // if the upload has already started.
        uploadSession.contentLength = totalBytes;
      }
    }

    @Override
    public void onInit(String runtime) {
      log.debug("Uploader {} initialized with runtime {}.", getConnectorId(),
          runtime);

      Plupload.this.runtime = Runtime.valueOf(runtime.toUpperCase());
      getState().rebuild = false;
    }
  }

  /**
   * The stream variable that maps the stream events to the upload component and
   * the configured data receiver.
   */
  private class StreamVariableImpl implements
      com.vaadin.server.StreamVariable {

    private int chunkContentLength;
    private int chunkCount;
    private int chunkIndex;

    @Override
    public boolean listenProgress() {
      return progressListeners != null && !progressListeners.isEmpty();
    }

    @Override
    public void onProgress(StreamVariable.StreamingProgressEvent event) {
      fireUpdateProgress(uploadSession.bytesRead + event.getBytesReceived(),
          uploadSession.contentLength);
    }

    @Override
    public boolean isInterrupted() {
      return uploadSession == null ? false : uploadSession.interrupted;
    }

    @Override
    public OutputStream getOutputStream() {

      boolean retryEnabled = getMaxRetries() > 0;
      boolean chunkEnabled = chunkCount > 1;

      if (uploadSession.receiverOutstream == null) {
        uploadSession.receiverOutstream =
            html5Receiver.receiveUpload(
                uploadSession.filename, uploadSession.mimeType,
                retryEnabled, chunkEnabled, chunkContentLength,
                uploadSession.contentLength);
      }

      // If retries are configured we need to be able to indicate when the
      // data is safe to write so we can throw it away in the event of a
      // failure.
      if (retryEnabled
          && !(uploadSession.receiverOutstream instanceof Html5Receiver.RetryableOutputStream)) {
        log.warn("Retries are enabled but the receiver output stream does "
            + "not implemented RetryableOutputStream. Duplicate data may be "
            + "written to the receiver in the event of a partial upload and "
            + "retry. Disable retries or use a RetryableOutputStream to "
            + "avoid this warning.");
      }

      return new org.mpilone.vaadin.upload.UncloseableOutputStream(
          uploadSession.receiverOutstream);
    }

    @Override
    public void streamingStarted(StreamVariable.StreamingStartEvent event) {

      Html5StreamVariable.Html5StreamingStartEvent html5Event =
          (Html5StreamVariable.Html5StreamingStartEvent) event;

      int contentLength = (int) event.getContentLength();

      String param = html5Event.getParameterValue("chunks");
      chunkCount = param != null ? Integer.parseInt(param) : 1;

      param = html5Event.getParameterValue("chunk");
      chunkIndex = param != null ? Integer.parseInt(param) : 0;

      chunkContentLength = contentLength;

      if (uploadSession == null) {
        startUpload();

        uploadSession.mimeType = event.getMimeType();
        uploadSession.filename = removePath(
            html5Event.getParameterValue("name"));

        // Plupload doesn't provide a total file size so we have to estimate
        // it from the chunk length * the number of chunks. We'll get a more
        // accurate value from the RPC calls.
        uploadSession.contentLength = contentLength * chunkCount;

        fireStarted(new StartedEvent(Plupload.this, uploadSession.filename,
            event.getMimeType(), contentLength));

        // Request the output stream here so it is initialized before we
        // start the first chunk to support initializing a retryable output
        // stream.
        getOutputStream();
      }

      if (uploadSession.receiverOutstream instanceof Html5Receiver.RetryableOutputStream) {
        try {
          ((Html5Receiver.RetryableOutputStream) uploadSession.receiverOutstream).
              chunkStart(
                  chunkIndex, chunkCount);
        }
        catch (IOException ex) {
          throw new RuntimeException("Unable to end chunk in retryable stream.",
              ex);
        }
      }
    }

    @Override
    public void streamingFinished(StreamVariable.StreamingEndEvent event) {

      Html5StreamVariable.Html5StreamingEndEvent html5Event =
          (Html5StreamVariable.Html5StreamingEndEvent) event;

      // Flush the retry stream if we are supporting retries.
      if (uploadSession.receiverOutstream instanceof Html5Receiver.RetryableOutputStream) {
        try {
          ((Html5Receiver.RetryableOutputStream) uploadSession.receiverOutstream).
              chunkEnd(
                  chunkIndex, chunkCount);
        }
        catch (IOException ex) {
          throw new RuntimeException("Unable to end chunk in retryable stream.",
              ex);
        }
      }

      // Update the total bytes read. This is needed because this stream
      // may only be one of many chunks.
      uploadSession.bytesRead += event.getBytesReceived();

      html5Event.setResponse(new Html5StreamVariable.UploadResponse(200,
          "text/plain", "{\"success\":true}"));

      // See if we're done with this upload.
      if (chunkCount == chunkIndex + 1) {
        org.mpilone.vaadin.upload.Streams.tryClose(
            uploadSession.receiverOutstream);

        // We delay the success event until we get the uploaded event from
        // the client. Plupload depends a lot on the DOM element being attached
        // to the document when the chunks complete so we don't to announce a
        // successful upload until we know that Plupload is done on the client
        // side and the server side component might be detached.
        uploadSession.succeededEventPending = true;
      }
    }

    @Override
    public void streamingFailed(StreamVariable.StreamingErrorEvent event) {
      Html5StreamVariable.Html5StreamingErrorEvent html5Event =
          (Html5StreamVariable.Html5StreamingErrorEvent) event;

      Exception exception = event.getException();
      String responseContent = "{\"success\": false}";

      if (exception instanceof FileUploadHandler.UploadInterruptedException) {
        // We respond and wait for the
        // interrupted state change to cancel the upload on the client side.
        responseContent = "{\"success\": false, \"error\": \"interrupted\", "
            + "\"preventRetry\": true}";
      }

      uploadSession.exception = exception;

      // Because we can't prevent retries on an HTML4 or non-chunked upload,
      // we'll delay ending the upload until we get the RPC call from the
      // client.
      html5Event.setResponse(new Html5StreamVariable.UploadResponse(400,
          "text/plain", responseContent));

      String msg = exception == null ? "unknown" : exception.getMessage();
      log.info("Streaming to receiver failed. The upload will be retried if "
          + "retries are configured and not exhausted. Exception: {}", msg);
    }
  }

  /**
   * The information related to a single upload session.
   */
  private static class UploadSession {

    OutputStream receiverOutstream;
    int contentLength;
    String filename;
    String mimeType;
    volatile long bytesRead;
    volatile boolean interrupted;
    boolean succeededEventPending;
    private boolean interruptedEventPending;
    private Exception exception;
  }

  /**
   * The error codes as defined by Plupload.
   */
  public enum ErrorCode {

    GENERIC_ERROR(-100),
    HTTP_ERROR(-200),
    IO_ERROR(-300),
    SECURITY_ERROR(-400),
    INIT_ERROR(-500),
    FILE_SIZE_ERROR(-600),
    FILE_EXTENSION_ERROR(-601),
    FILE_DUPLICATE_ERROR(-602),
    IMAGE_FORMAT_ERROR(-700),
    IMAGE_MEMORY_ERROR(-701),
    IMAGE_DIMENSIONS_ERROR(-702);

    private final Integer code;

    private ErrorCode(int code) {
      this.code = code;
    }

    public Integer getCode() {
      return code;
    }
  }

  /**
   * The available client side runtimes.
   */
  public enum Runtime {

    HTML4,
    FLASH,
    SILVERLIGHT,
    HTML5
  }

}
