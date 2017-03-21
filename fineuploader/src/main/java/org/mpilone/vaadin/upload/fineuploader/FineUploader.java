package org.mpilone.vaadin.upload.fineuploader;

import static org.mpilone.vaadin.upload.Streams.tryClose;

import java.io.IOException;
import java.io.OutputStream;

import org.mpilone.vaadin.upload.*;
import org.mpilone.vaadin.upload.Html5Receiver.RetryableOutputStream;
import org.mpilone.vaadin.upload.fineuploader.shared.FineUploaderClientRpc;
import org.mpilone.vaadin.upload.fineuploader.shared.FineUploaderServerRpc;
import org.mpilone.vaadin.upload.fineuploader.shared.FineUploaderState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.annotations.JavaScript;
import com.vaadin.server.*;
import com.vaadin.server.communication.FileUploadHandler;
import com.vaadin.ui.Upload;

/**
 * <p>
 * Wrapper for the FineUploader HTML5/HTML4 upload component. You can find more
 * information at http://fineuploader.com/. This implementation attempts to
 * follow the {@link Upload} API as much as possible to be a drop-in
 * replacement.
 * </p>
 *
 * @author mpilone
 */
@JavaScript({"fineuploader_connector.js",
  "fineuploader/fineuploader-5.0.8.min.js"})
public class FineUploader extends AbstractHtml5Upload {

  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = 1L;

  /**
   * The log for this class.
   */
  private final static Logger log = LoggerFactory.getLogger(FineUploader.class);

  private final FineUploaderServerRpc serverRpc
      = new ServerRpcImpl();
  private final FineUploaderClientRpc clientRpc;
  private StreamVariable streamVariable;
  private UploadSession uploadSession;

  /**
   * Constructs the upload component.
   *
   * @see #FineUploader(java.lang.String, com.vaadin.ui.Upload.Receiver)
   */
  public FineUploader() {
    this(null, null);
  }

  /**
   * Constructs the upload component. The following defaults will be used:
   * <ul>
   * <li>chunkSize: 0 (i.e. disabled)</li>
   * <li>maxFileSize: 10MB</li>
   * <li>maxRetries: 0 (i.e. disabled)</li>
   * </ul>
   *
   * @param caption the caption of the component
   * @param receiver the receiver to create the output stream to receive upload
   * data
   */
  public FineUploader(String caption, Upload.Receiver receiver) {
    registerRpc(serverRpc);
    clientRpc = getRpcProxy(FineUploaderClientRpc.class);

    setCaption(caption);
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
        getStreamVariableTargetUrl(this, "fineuploader", getStreamVariable());

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
        "fineuploader");

    super.detach();
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
    return getState(false).buttonCaption;
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
   * Sets the immediate mode flag. A value of true will cause the upload to
   * begin as soon as the user selects a file.
   * 
   * @param immediate
   *          true for immediate, false to require manual submission using
   *          {@link #submitUpload()}
   */
  public void setImmediateMode(boolean immediate) {
    getState().immediateMode = immediate;
    getState().rebuild = true;
  }

  /**
   * Returns true if the uploader is configured to submit the upload immediately
   * after file selection.
   * 
   * @return true if the upload will submit immediately after file selection
   */
  public boolean isImmediateMode() {
    return getState(false).immediateMode;
  }

  /**
   * Returns the maximum number of retries if an upload fails.
   *
   * @return the number of retries
   */
  public long getMaxRetries() {
    return getState(false).maxRetries;
  }

  /**
   * Sets the maximum number of retries if an upload fails. FineUploader
   * maintains a single retry count for the entire upload and does not reset it
   * per chunk.
   *
   * @param maxRetries the number of retries
   */
  public void setMaxRetries(int maxRetries) {
    getState().maxRetries = maxRetries;
    getState().rebuild = true;
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
    getState().rebuild = true;
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
    getState().rebuild = true;
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
   * done by the receiving tread so this method will return immediately and the
   * actual interrupt will happen a bit later.
   */
  public void interruptUpload() {
    if (uploadSession != null) {
      uploadSession.interrupted = true;
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
        fireUploadSuccess(new SucceededEvent(FineUploader.this,
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
        fireUploadInterrupted(new FailedEvent(FineUploader.this,
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

  @Override
  protected FineUploaderState getState() {
    return (FineUploaderState) super.getState();
  }

  @Override
  protected FineUploaderState getState(boolean markAsDirty) {
    return (FineUploaderState) super.getState(markAsDirty);
  }

  /**
   * The remote procedure call interface which allows calls from the client side
   * to the server. For the most part these methods map to the events generated
   * by the FileUploader JavaScript component.
   */
  private class ServerRpcImpl implements FineUploaderServerRpc {

    /**
     * Serialization ID.
     */
    private static final long serialVersionUID = 1L;

    @Override
    public void onError(Integer id, String name, String errorReason) {

      log.info("Error on upload. id: {}, name: {}, reason: {}", id, name,
          errorReason);

      if (errorReason != null && errorReason.contains("too large")) {
        fireFileSizeExceeded(
            new FileSizeExceededEvent(FineUploader.this, name, null, -1));
      }

      endUpload();
    }

    @Override
    public void onComplete(int id, String name) {
      endUpload();
    }

    @Override
    public void onProgress(int id, String name, int uploadedBytes,
        int totalBytes) {
      // Ignore. We want the call to refresh uploader state (i.e. polling)
      // but we don't care about the progress value.
    }

    @Override
    public void onInit(String runtime) {
      log.debug("Uploader {} initialized.", getConnectorId());

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
          && !(uploadSession.receiverOutstream instanceof RetryableOutputStream)) {
        log.warn("Retries are enabled but the receiver output stream does "
            + "not implemente RetryableOutputStream. Duplicate data may be "
            + "written to the receiver in the event of a partial upload and "
            + "retry. Disable retries or use a RetryableOutputStream to "
            + "avoid this warning.");
      }

      return new UncloseableOutputStream(uploadSession.receiverOutstream);
    }

    @Override
    public void streamingStarted(StreamVariable.StreamingStartEvent event) {

      Html5StreamVariable.Html5StreamingStartEvent html5Event =
          (Html5StreamVariable.Html5StreamingStartEvent) event;

      String param = html5Event.getParameterValue("qqtotalfilesize");
      int contentLength = param != null ? Integer.parseInt(param) : -1;

      param = html5Event.getParameterValue("qqtotalparts");
      chunkCount = param != null ? Integer.parseInt(param) : 1;

      param = html5Event.getParameterValue("qqpartindex");
      chunkIndex = param != null ? Integer.parseInt(param) : 0;

      param = html5Event.getParameterValue("qqchunksize");
      chunkContentLength = param != null ? Integer.parseInt(param) :
          (int) event.getContentLength();

      if (uploadSession == null) {
        startUpload();

        uploadSession.mimeType = event.getMimeType();
        uploadSession.filename = Streams.removePath(html5Event.
            getParameterValue("qqfilename"));
        uploadSession.contentLength = contentLength;

        fireStarted(new StartedEvent(FineUploader.this, uploadSession.filename,
            event.getMimeType(), contentLength));

        // Request the output stream here so it is initialized before we 
        // start the first chunk to support initializing a retryable output
        // stream.
        getOutputStream();
      }

      if (uploadSession.receiverOutstream instanceof RetryableOutputStream) {
        try {
          ((RetryableOutputStream) uploadSession.receiverOutstream).chunkStart(
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
      if (uploadSession.receiverOutstream instanceof RetryableOutputStream) {
        try {
          ((RetryableOutputStream) uploadSession.receiverOutstream).chunkEnd(
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
      if (chunkIndex + 1 == chunkCount) {
        Streams.tryClose(uploadSession.receiverOutstream);

        // Delay firing the event until the client tells us it is done.
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
      html5Event.setResponse(new Html5StreamVariable.UploadResponse(200,
          "text/plain", responseContent));

      String msg = exception == null ? "unknown" : exception.getMessage();
      log.info("Streaming to receiver failed. The upload will be retried if "
          + "retries are configured and not exhausted. Exception: {}", msg);

      html5Event.setResponse(new Html5StreamVariable.UploadResponse(200,
          "text/plain", responseContent));
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
    private boolean succeededEventPending;
    private Exception exception;
  }

}
