package org.mpilone.vaadin.upload;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import com.vaadin.server.RequestHandler;
import com.vaadin.ui.AbstractJavaScriptComponent;
import com.vaadin.ui.Component;
import com.vaadin.ui.Upload;

/**
 * A base class for all HTML5 upload component implementations that provides
 * basic event handling and registration of the {@link Html5FileUploadHandler}.
 *
 * @author mpilone
 */
public abstract class AbstractHtml5Upload extends AbstractJavaScriptComponent {

  private final static Method SUCCEEDED_METHOD;
  private final static Method STARTED_METHOD;
  private final static Method FINISHED_METHOD;
  private final static Method FAILED_METHOD;

  static {
    try {
      SUCCEEDED_METHOD = SucceededListener.class.getMethod(
          "uploadSucceeded", SucceededEvent.class);
      FAILED_METHOD = FailedListener.class.
          getMethod("uploadFailed", FailedEvent.class);
      STARTED_METHOD = StartedListener.class.getMethod(
          "uploadStarted", StartedEvent.class);
      FINISHED_METHOD = FinishedListener.class.getMethod(
          "uploadFinished", FinishedEvent.class);
    }
    catch (NoSuchMethodException | SecurityException ex) {
      throw new RuntimeException("Unable to find listener event method.", ex);
    }
  }

  /**
   * The list of progress listeners to be notified during the upload.
   */
  protected final List<Upload.ProgressListener> progressListeners =
      new ArrayList<>();

  /**
   * The receiver registered with the upload component that all data will be
   * streamed into.
   */
  protected Upload.Receiver receiver;

  /**
   * The HTML5 wrapper on the receiver. This may or may not be the same as the
   * {@link #receiver} registered with the upload depending on if the register
   * receiver implements the HTML5 receiver interface.
   */
  protected Html5Receiver html5Receiver;

  /**
   * Installs the {@link Html5FileUploadHandler} into the session if it is not
   * already registered. This should be called when an HTML5 uploader is
   * attached to the UI. It is safe to call this method multiple times and only
   * a single handler will be installed for the session.
   */
  protected void installHandler() {
    // See if the uploader handler is already installed for this session.
    boolean handlerInstalled = false;
    for (RequestHandler handler : getSession().getRequestHandlers()) {
      if (handler instanceof Html5FileUploadHandler) {
        handlerInstalled = true;
      }
    }

    // Install the upload handler if one is not already registered.
    if (!handlerInstalled) {
      getSession().addRequestHandler(new Html5FileUploadHandler());
    }
  }

  /**
   * Returns true if the component is enabled. This implementation always
   * returns true even if the component is set to disabled. This is required
   * because we want the ability to disable the browse/submit buttons while
   * still allowing an upload in progress to continue. The implementation relies
   * on RPC calls so the overall component must always be enabled or the upload
   * complete RPC call will be dropped.
   *
   * @return always true
   */
  @Override
  public boolean isConnectorEnabled() {
    return true;
  }

  /**
   * Fires the upload success event to all registered listeners.
   *
   * @param evt the event details
   */
  protected void fireUploadSuccess(SucceededEvent evt) {
    fireEvent(evt);
  }

  /**
   * Fires the the progress event to all registered listeners.
   *
   * @param totalBytes bytes received so far
   * @param contentLength actual size of the file being uploaded, if known
   *
   */
  protected void fireUpdateProgress(long totalBytes, long contentLength) {
    // This is implemented differently than other listeners to maintain
    // backwards compatibility
    if (progressListeners != null) {
      for (Upload.ProgressListener l : progressListeners) {
        l.updateProgress(totalBytes, contentLength);
      }
    }
  }

  /**
   * Adds the given listener for upload failed events.
   *
   * @param listener the listener to add
   */
  public void addFailedListener(FailedListener listener) {
    addListener(FailedEvent.class, listener, FAILED_METHOD);
  }

  /**
   * Adds the given listener for upload finished events.
   *
   * @param listener the listener to add
   */
  public void addFinishedListener(FinishedListener listener) {
    addListener(FinishedEvent.class, listener, FINISHED_METHOD);
  }

  /**
   * Adds the given listener for upload progress events.
   *
   * @param listener the listener to add
   */
  public void addProgressListener(Upload.ProgressListener listener) {
    progressListeners.add(listener);
  }

  /**
   * Adds the given listener for upload started events.
   *
   * @param listener the listener to add
   */
  public void addStartedListener(StartedListener listener) {
    addListener(StartedEvent.class, listener, STARTED_METHOD);
  }

  /**
   * Adds the given listener for upload succeeded events.
   *
   * @param listener the listener to add
   */
  public void addSucceededListener(SucceededListener listener) {
    addListener(SucceededEvent.class, listener, SUCCEEDED_METHOD);
  }

  /**
   * Removes the given listener for upload failed events.
   *
   * @param listener the listener to add
   */
  public void removeFailedListener(FailedListener listener) {
    removeListener(FailedEvent.class, listener, FAILED_METHOD);
  }

  /**
   * Removes the given listener for upload finished events.
   *
   * @param listener the listener to add
   */
  public void removeFinishedListener(FinishedListener listener) {
    removeListener(FinishedEvent.class, listener, FINISHED_METHOD);
  }

  /**
   * Removes the given listener for upload progress events.
   *
   * @param listener the listener to add
   */
  public void removeProgressListener(Upload.ProgressListener listener) {
    progressListeners.remove(listener);
  }

  /**
   * Removes the given listener for upload started events.
   *
   * @param listener the listener to add
   */
  public void removeStartedListener(StartedListener listener) {
    removeListener(StartedEvent.class, listener, STARTED_METHOD);
  }

  /**
   * Removes the given listener for upload succeeded events.
   *
   * @param listener the listener to add
   */
  public void removeSucceededListener(SucceededListener listener) {
    removeListener(SucceededEvent.class, listener, SUCCEEDED_METHOD);
  }

  /**
   * Fires the upload started event to all registered listeners.
   *
   * @param evt the started event to fire
   */
  protected void fireStarted(StartedEvent evt) {
    fireEvent(evt);
  }

  /**
   * Fires the no input stream error event to all registered listeners.
   *
   * @param filename the name of the file provided by the client
   * @param mimeType the mime-type provided by the client
   * @param length the length/size of the file received
   */
  protected void fireNoInputStream(String filename, String mimeType,
      long length) {
    fireEvent(new NoInputStreamEvent(this, filename, mimeType,
        length));
  }

  /**
   * Fires the no output stream error event to all registered listeners.
   *
   * @param filename the name of the file provided by the client
   * @param mimeType the mime-type provided by the client
   * @param length the length/size of the file received
   */
  protected void fireNoOutputStream(String filename, String mimeType,
      long length) {
    fireEvent(new NoOutputStreamEvent(this, filename, mimeType,
        length));
  }

  /**
   * Fires the file size exceeded error event to all registered listeners.
   *
   * @param evt the event details
   */
  protected void fireFileSizeExceeded(FileSizeExceededEvent evt) {
    fireEvent(evt);
  }

  /**
   * Fires the upload interrupted error event to all registered listeners.
   *
   * @param evt the event details
   */
  protected void fireUploadInterrupted(FailedEvent evt) {
    fireEvent(evt);
  }

  /**
   * Returns the receiver that will be used to create output streams when a file
   * starts uploading.
   *
   * @return the receiver for all incoming data
   */
  public Upload.Receiver getReceiver() {
    return receiver;
  }

  /**
   * Sets the receiver that will be used to create output streams when a file
   * starts uploading. The file data will be written to the returned stream. If
   * not set, the uploaded data will be ignored. If the receiver implements
   * {@link Html5Receiver} it will be used directly, otherwise it will be
   * wrapped in a {@link DefaultHtml5Receiver} to handle chunking and retries
   * properly.
   *
   * @param receiver the receiver to use for creating file output streams
   */
  public void setReceiver(Upload.Receiver receiver) {
    this.receiver = receiver;

    if (receiver == null) {
      this.html5Receiver = null;
    }
    else if (receiver instanceof Html5Receiver) {
      this.html5Receiver = (Html5Receiver) receiver;
    }
    else {
      this.html5Receiver = new DefaultHtml5Receiver(receiver);
    }
  }

  /**
   * The event fired when an upload completes, both success or failure.
   */
  public static class FinishedEvent extends Component.Event {

    private final String filename;
    private final String mimeType;
    private final long length;

    /**
     * Constructs the event.
     *
     * @param source the source component
     * @param filename the name of the file provided by the client
     * @param mimeType the mime-type provided by the client
     * @param length the content length in bytes provided by the client
     */
    public FinishedEvent(Component source, String filename, String mimeType,
        long length) {
      super(source);

      this.filename = filename;
      this.mimeType = mimeType;
      this.length = length;
    }

    /**
     * Returns the file name.
     *
     * @return the file name
     */
    public String getFilename() {
      return filename;
    }

    /**
     * Returns the mime-type.
     *
     * @return the mime-type
     */
    public String getMimeType() {
      return mimeType;
    }

    /**
     * Returns the content length in bytes.
     *
     * @return the length in bytes
     */
    public long getLength() {
      return length;
    }

  }

  /**
   * A failed event describing the maximum file size exceeded on the client
   * side. This event will occur before the upload is ever started.
   */
  public static class FileSizeExceededEvent extends FailedEvent {

    /**
     * Constructs the event.
     *
     * @param source the source component
     * @param filename the name of the file provided by the client
     * @param mimeType the mime-type provided by the client
     * @param length the content length in bytes provided by the client
     */
    public FileSizeExceededEvent(Component source, String filename,
        String mimeType, long length) {
      super(source, filename, mimeType, length, null);
    }
  }

  /**
   * A failed event describing no input stream available for reading.
   */
  public static class NoInputStreamEvent extends FailedEvent {

    /**
     * Constructs the event.
     *
     * @param source the source component
     * @param filename the name of the file provided by the client
     * @param mimeType the mime-type provided by the client
     * @param length the content length in bytes provided by the client
     */
    public NoInputStreamEvent(Component source, String filename, String mimeType,
        long length) {
      super(source, filename, mimeType, length, null);
    }
  }

  /**
   * A failed event describing no output stream available for reading.
   */
  public static class NoOutputStreamEvent extends FailedEvent {

    /**
     * Constructs the event.
     *
     * @param source the source component
     * @param filename the name of the file provided by the client
     * @param mimeType the mime-type provided by the client
     * @param length the content length in bytes provided by the client
     */
    public NoOutputStreamEvent(Component source, String filename,
        String mimeType, long length) {
      super(source, filename, mimeType, length, null);
    }
  }

  /**
   * A listener for finished events.
   */
  public interface FinishedListener {

    /**
     * Called when an upload finishes, either success or failure.
     *
     * @param evt the event describing the completion
     */
    void uploadFinished(FinishedEvent evt);
  }

  /**
   * An event describing an upload failure.
   */
  public static class FailedEvent extends FinishedEvent {

    private final Exception reason;

    /**
     * Constructs the event.
     *
     * @param source the source component
     * @param filename the name of the file provided by the client
     * @param mimeType the mime-type provided by the client
     * @param length the content length in bytes provided by the client
     * @param reason the root cause exception
     */
    public FailedEvent(Component source, String filename, String mimeType,
        long length, Exception reason) {
      super(source, filename, mimeType, length);
      this.reason = reason;
    }

    /**
     * Returns the root cause exception if available.
     *
     * @return the root exception
     */
    public Exception getReason() {
      return reason;
    }
  }

  /**
   * A listener for failed events.
   */
  public interface FailedListener {

    /**
     * Called when an upload fails.
     *
     * @param evt the event details
     */
    void uploadFailed(FailedEvent evt);
  }

  /**
   * An event describing the start of an upload.
   */
  public static class StartedEvent extends Component.Event {

    private final String filename;
    private final String mimeType;
    private final long contentLength;

    /**
     * Constructs the event.
     *
     * @param source the source component
     * @param filename the name of the file provided by the client
     * @param mimeType the mime-type provided by the client
     * @param contentLength the content length in bytes provided by the client
     */
    public StartedEvent(Component source, String filename, String mimeType,
        long contentLength) {
      super(source);
      this.filename = filename;
      this.mimeType = mimeType;
      this.contentLength = contentLength;
    }

    /**
     * The file name provided by the client.
     *
     * @return the file name
     */
    public String getFilename() {
      return filename;
    }

    /**
     * The mime-type provided by the client.
     *
     * @return the mime-type
     */
    public String getMimeType() {
      return mimeType;
    }

    /**
     * The content length in bytes provided by the client.
     *
     * @return the content length
     */
    public long getContentLength() {
      return contentLength;
    }
  }

  /**
   * A listener that receives started events.
   */
  public interface StartedListener {

    /**
     * Called when the upload is started.
     *
     * @param evt the event details
     */
    void uploadStarted(StartedEvent evt);
  }

  /**
   * An event describing a successful upload.
   */
  public static class SucceededEvent extends FinishedEvent {

    /**
     * Constructs the event.
     *
     * @param source the source component
     * @param filename the name of the file provided by the client
     * @param mimeType the mime-type provided by the client
     * @param length the content length in bytes provided by the client
     */
    public SucceededEvent(Component source, String filename, String mimeType,
        long length) {
      super(source, filename, mimeType, length);
    }

  }

  /**
   * A listener that receives upload success events.
   */
  public interface SucceededListener {

    /**
     * Called when an upload is successful.
     *
     * @param evt the event details
     */
    void uploadSucceeded(SucceededEvent evt);
  }

}
