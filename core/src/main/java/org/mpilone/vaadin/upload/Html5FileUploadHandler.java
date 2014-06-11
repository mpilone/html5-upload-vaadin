package org.mpilone.vaadin.upload;

import static com.vaadin.server.communication.FileUploadHandler.DEFAULT_STREAMING_PROGRESS_EVENT_INTERVAL_MS;
import static java.lang.String.format;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

import javax.servlet.http.*;

import org.apache.commons.fileupload.*;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import com.vaadin.server.*;
import com.vaadin.server.communication.*;
import com.vaadin.ui.UI;


/**
 * A custom file upload request handler that generates
 * {@link Html5StreamVariable} events to allow the stream variable lower level
 * access to upload data such as form parameters and response messages that are
 * needed for HTML5, AJAX style uploaders such as FineUploader and Plupload. It
 * would be nice if this functionality would be rolled into the standard Vaadin
 * {@link FileUploadHandler} in the future.
 *
 * @author mpilone
 */
public class Html5FileUploadHandler implements RequestHandler {

  /**
   * The UTF-8 character set for decoding content.
   */
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  /**
   * The byte size of the "\r\n" after a header.
   */
  private static final int CRLF_SIZE = 2;

  /**
   * The byte size of the "--" before or after the part boundary.
   */
  private static final int DASH_DASH_SIZE = 2;

  /**
   * The byte size of the ": " after a header.
   */
  private static final int COLON_SPACE_SIZE = 2;

  /**
   * The URL prefix that this handler will handle. All requests matching this
   * request URL will be assumed to be uploads.
   */
  public static final String URL_PREFIX = "APP/HTML5_FILE_UPLOAD/";

  @Override
  public boolean handleRequest(final VaadinSession session,
      VaadinRequest request,
      VaadinResponse response) throws IOException {

    // Most of this initial URL handling code is taken from
    // com.vaadin.server.communication.FileUploadHandler. Things begin to
    // differ once we get into handling the actual request data.
    if (!hasPathPrefix(request, URL_PREFIX)) {
      return false;
    }

    // Expected URI pattern: APP/HTML5_FILE_UPLOAD/[UIID]/[PID]/[NAME]/[SECKEY]
    String pathInfo = request.getPathInfo();

    // Dtrip away part until the data we are interested starts
    int startOfData = pathInfo.indexOf(URL_PREFIX) + URL_PREFIX.length();
    String uppUri = pathInfo.substring(startOfData);

    // 0 = UI ID, 1 = connector ID, 2= name, 3 = sec key
    final String[] parts = uppUri.split("/", 4);
    final String uiId = parts[0];
    final String connectorId = parts[1];
    final String variableName = parts[2];

    // Populate initial fields while session is locked.
    final UploadContext context = new UploadContext();

    runInLock(session, new Runnable() {
      @Override
      public void run() {
        UI uI = session.getUIById(Integer.parseInt(uiId));
        UI.setCurrent(uI);

        StreamVariable streamVariable = uI.getConnectorTracker().
            getStreamVariable(
                connectorId, variableName);
        String secKey = uI.getConnectorTracker().getSeckey(streamVariable);

        if (secKey.equals(parts[3])) {
          context.streamVariable = streamVariable;
          context.source = session.getCommunicationManager().getConnector(uI,
              connectorId);
        }
      }
    });

    if (context.streamVariable == null || context.source == null) {
      // TODO: rethink error handling here.
      return true;
    }

    String contentLengthHeader = request.
        getHeader(FileUploadBase.CONTENT_LENGTH);
    
    context.contentLength = contentLengthHeader != null ? Integer.parseInt(
        contentLengthHeader) : -1;
    context.dataContentLength = context.contentLength;
    context.contentType = request.getHeader(FileUploadBase.CONTENT_TYPE);
    context.request = request;
    context.response = response;
    context.servletRequest = asServletRequest(request);
    context.session = session;
    context.params = new HashMap<>();

    // Copy over the URL parameters into our own map so they can be provided
    // to the stream variable implementation.
    context.addParams(request.getParameterMap());

    handleRequest(context);

    return true;
  }

  /**
   * Extracts the HTTP servlet request from the given Vaadin request after some
   * error checking.
   *
   * @param request the HTTP request providing the servlet request
   *
   * @return the request as a servlet request
   */
  private HttpServletRequest asServletRequest(VaadinRequest request) {
    if (!(request instanceof VaadinServletRequest)) {
      throw new IllegalArgumentException(format("Expected %s but found %s.",
          VaadinServletRequest.class, request.getClass()));
    }

    return ((VaadinServletRequest) request).getHttpServletRequest();
  }

  /**
   * Handles the given request for upload after the required fields have been
   * extracted from the URL and put into the upload context.
   *
   * @param context the upload context including the request, session, and
   * stream variable
   */
  private void handleRequest(UploadContext context) {

    boolean isMultipart = ServletFileUpload.isMultipartContent(
        context.servletRequest);

    if (isMultipart) {
      handleMultipartRequest(context);
    }
    else {
      throw new UnsupportedOperationException("Not supported. Client should "
          + "use multipart upload.");
    }
  }

  /**
   * Handles the given multipart request for upload after the required fields
   * have been extracted from the URL and put into the upload context.
   *
   * @param context the upload context including the request, session, and
   * stream variable
   */
  private void handleMultipartRequest(final UploadContext context) {

    Html5StreamVariable.UploadResponse response = null;

    // Determine the boundary so the content length of the final file
    // part can be calculated accurately.
    String contentDispositionHeader = context.request.getHeader(
        FileUploadBase.CONTENT_TYPE);
    int pos = contentDispositionHeader.indexOf("boundary=");
    String boundary = contentDispositionHeader.substring(pos + "boundary=".
        length());

    // Create a new Apache commons file upload handler.
    ServletFileUpload upload = new ServletFileUpload();

    try {
      // Parse the request and iterate over the parts.
      FileItemIterator iter = upload.getItemIterator(context.servletRequest);
      while (iter.hasNext()) {
        FileItemStream item = iter.next();
        String name = item.getFieldName();

        try (InputStream stream = item.openStream()) {

          if (item.isFormField()) {
            // The item is a simple form field. Copy the name and value into 
            // the parameters map so they can be provided to the stream
            // variable.
            String value = Streams.asString(stream);
            context.addParam(name, value);
            context.dataContentLength -=
                calcTotalItemSize(boundary, value, item.getHeaders());
          }
          else {
            // The item is a the file data to be uploaded. Stream the data
            // to the receiver variable.
            context.filename = Streams.removePath(item.getName());
            context.contentType = item.getContentType();
            context.dataContentLength -=
                calcTotalItemSize(boundary, null, item.getHeaders());

            response = streamToReceiver(stream, context);
          }
        }
      }

      // If no custom response was set, create a default.
      if (response == null) {
        response = new Html5StreamVariable.UploadResponse(
            HttpServletResponse.SC_OK, "text/plain", "Upload Successful");
      }

      // Write the response to the client.
      try (Writer writer = context.response.getWriter()) {
        context.response.setStatus(response.getStatusCode());
        context.response.setContentType(response.getContentType());
        writer.append(response.getContent());
      }
    }
    catch (IOException | FileUploadException | UploadException e) {
      runInLock(context.session, new Runnable() {
        @Override
        public void run() {
          context.session.getCommunicationManager()
              .handleConnectorRelatedException(context.source, e);
        }
      });
    }
  }

  /**
   * Calculates the byte size of the multipart item given the current boundary,
   * item value, and any item headers. If the value is null, the item is assumed
   * to be a file and the size will include the trailing boundary (and
   * associated dash-dash).
   *
   * @param boundary the multipart boundary
   * @param value the value/data in the item
   * @param headers the item headers
   *
   * @return the total size including expected line feeds and boundary dashes
   */
  private static int calcTotalItemSize(String boundary,
      String value, FileItemHeaders headers) {

    // Item Boundary
    int totalLength = DASH_DASH_SIZE + boundary.getBytes(UTF_8).length
        + CRLF_SIZE;

    // Item headers
    for (Iterator<String> iter = headers.getHeaderNames(); iter.hasNext();) {
      String headerName = iter.next();

      for (Iterator<String> iter2 = headers.getHeaders(headerName); iter2.
          hasNext();) {
        totalLength += headerName.getBytes(UTF_8).length + COLON_SPACE_SIZE;

        String headerValue = iter2.next();
        totalLength += headerValue.getBytes(UTF_8).length + CRLF_SIZE;
      }
    }

    // Blank line after headers.
    totalLength += CRLF_SIZE;

    // Item value (may not be set if this is the final file part).
    if (value != null) {
      totalLength += value.getBytes(UTF_8).length + CRLF_SIZE;
    }
    else {
      // This is the file part so we add the trailing boundary.
      totalLength += CRLF_SIZE;
      totalLength += DASH_DASH_SIZE + boundary.getBytes(UTF_8).length
          + DASH_DASH_SIZE + CRLF_SIZE;
    }

    return totalLength;
  }

  /**
   * Runs the given task in the session after acquiring the session lock. This
   * is a thin wrapper over {@link VaadinSession#accessSynchronously(java.lang.Runnable)
   * } to support different strategies.
   *
   * @param session the session to lock
   * @param task the task to run
   */
  private static void runInLock(VaadinSession session, Runnable task) {
    // The original Vaadin file upload handler does session locking
    // manually using VaadinSession#lock(); however it seems to be safer 
    // (although maybe a bit slower) to use the new accessSynchronously method.
    session.accessSynchronously(task);
  }

  /**
   * Streams all the data in the input stream to the receiver's output stream.
   * Proper session locking will be done so the streaming events are dispatched
   * within the session/UI lock while the raw data streaming is done outside the
   * lock to prevent blocking the application while data is received.
   *
   * @param in the input stream to read from
   * @param context the current upload context including the target stream
   * variable and session to lock
   *
   * @return the response if set by the stream variable or null
   * @throws UploadException if an error occurs while streaming the data
   */
  private Html5StreamVariable.UploadResponse streamToReceiver(InputStream in,
      final UploadContext context) throws UploadException {

    // Grab some fields from the context for quick access.
    final VaadinSession session = context.session;
    final StreamVariable streamVariable = context.streamVariable;

    Html5StreamVariable.UploadResponse response = null;
    OutputStream out = null;
    boolean listenProgress;

    try {
      // Fire the started event and determine if the receiver wants to
      // be notified of progress events.
      final StreamingStartEventImpl startedEvent =
          new StreamingStartEventImpl(context);

      final Object[] outValues = new Object[2];
      runInLock(session, new Runnable() {
        @Override
        public void run() {
          streamVariable.streamingStarted(startedEvent);
          outValues[0] = streamVariable.getOutputStream();
          outValues[1] = streamVariable.listenProgress();
        }
      });

      out = (OutputStream) outValues[0];
      listenProgress = (boolean) outValues[1];

      if (out == null) {
        // No output stream to write to.
        throw new NoOutputStreamException();
      }

      if (in == null) {
        // No input stream to read from.
        throw new NoInputStreamException();
      }

      // Stream the data using a simple memory buffer.
      final byte buffer[] = new byte[Streams.IO_BUFFER_SIZE];
      int bytesRead;
      long lastProgressEventTime = 0;
      while ((bytesRead = in.read(buffer)) > 0) {
        out.write(buffer, 0, bytesRead);
        context.dataRead += bytesRead;

        // To avoid excessive session locking and event storms,
        // events are sent in intervals, or at the end of the file.
        long now = System.currentTimeMillis();
        boolean readyProgress = lastProgressEventTime
            + getProgressEventInterval() <= now;
        boolean completeProgress = context.dataRead == context.dataContentLength;

        // If the receiver is interested in progress events and ready 
        // for the next event (i.e. enough time has elapsed) or we're at 
        // the end of the data, lock the session and fire a new one.
        if (listenProgress && (readyProgress || completeProgress)) {
          lastProgressEventTime = now;
          final StreamingProgressEventImpl progressEvent =
              new StreamingProgressEventImpl(context);

          runInLock(session, new Runnable() {
            @Override
            public void run() {
              streamVariable.onProgress(progressEvent);
            }
          });
        }

        // Check if the server side interrupted the upload. If so, we should
        // attempt to abort the receiving process as soon as possible.
        if (streamVariable.isInterrupted()) {
          throw new FileUploadHandler.UploadInterruptedException();
        }
      }

      // Upload successful. Fire the end event.
      out.close();
      final StreamingEndEventImpl event = new StreamingEndEventImpl(context);

      runInLock(session, new Runnable() {
        @Override
        public void run() {
          streamVariable.streamingFinished(event);
        }
      });

      response = event.getResponse();
    }
    catch (Exception e) {
//    catch (FileUploadHandler.UploadInterruptedException  e) {
      // Download interrupted by application code. Relay the error to the
      // stream variable and use the custom response if set.
      //
      // Note, we are not throwing interrupted exception forward as it is
      // not a terminal level error like all other exception.
      Streams.tryClose(out);
      final StreamingErrorEventImpl event =
          new StreamingErrorEventImpl(context, e);

      runInLock(session, new Runnable() {
        @Override
        public void run() {
          streamVariable.streamingFailed(event);
        }
      });

      response = event.getResponse();
    }
//    catch (Exception e) {
//      // Download interrupted by an unexpected error. Replay the error to
//      // the stream variable and raise an exception.
//      Streams.tryClose(out);
//      final StreamingErrorEvent event = new StreamingErrorEventImpl(context, e);
//
//      runInLock(session, new Runnable() {
//        @Override
//        public void run() {
//          streamVariable.streamingFailed(event);
//        }
//      });
//
//      // throw exception for terminal to be handled (to be passed to
//      // terminalErrorHandler)
//      throw new UploadException(e);
//    }

    return response;
  }

  /**
   * To prevent event storming, streaming progress events are sent in this
   * interval rather than every time the buffer is filled. This fixes #13155. To
   * adjust this value override the method, and register your own handler in
   * VaadinService.createRequestHandlers(). The default is 500ms, and setting it
   * to 0 effectively restores the old behavior.
   *
   * @return the desired delay in MS between progress events
   */
  protected int getProgressEventInterval() {
    return DEFAULT_STREAMING_PROGRESS_EVENT_INTERVAL_MS;
  }

  /**
   * Returns true if the given request's path starts with the given prefix. This
   * method handles automatically adding a leading '/' if required.
   *
   * @param request the request to examine
   * @param prefix the path to check against the request
   *
   * @return true if the request starts with the given prefix
   */
  private static boolean hasPathPrefix(VaadinRequest request, String prefix) {
    String pathInfo = request.getPathInfo();

    if (pathInfo == null) {
      return false;
    }

    if (!prefix.startsWith("/")) {
      prefix = '/' + prefix;
    }

    return (pathInfo.startsWith(prefix));
  }

  /**
   * The context of the current upload. This is a simple structure to hold all
   * the information that must be tracked during a single upload.
   */
  private static class UploadContext {

    public VaadinRequest request;
    public VaadinResponse response;
    public HttpServletRequest servletRequest;
    public VaadinSession session;
    public String contentType;
    public int contentLength = -1;
    public int dataContentLength = -1;
    public int dataRead = 0;
    public String filename;
    public StreamVariable streamVariable;
    public ClientConnector source;
    public Map<String, Collection<String>> params;

    /**
     * Adds all the parameters defined in the map to the {@link #params} field.
     *
     * @param paramMap the map of parameters to add
     */
    public void addParams(Map<String, String[]> paramMap) {
      for (Map.Entry<String, String[]> entry : paramMap.entrySet()) {
        addParams(entry.getKey(), entry.getValue());
      }
    }

    /**
     * Adds all the values for the given parameter to the {@link #params} field.
     *
     * @param name the name of the parameter
     * @param values the values of the parameter
     */
    public void addParams(String name, String[] values) {
      for (String value : values) {
        addParam(name, value);
      }
    }

    /**
     * Adds the value for the given parameter to the {@link #params} field.
     *
     * @param name the name of the parameter
     * @param value the value of the parameter
     */
    public void addParam(String name, String value) {
      if (!params.containsKey(name)) {
        params.put(name, new ArrayList<String>());
      }
      params.get(name).add(value);
    }
  }

  /**
   * Base class for the streaming event implementations.
   */
  private static abstract class AbstractStreamingEvent implements
      StreamVariable.StreamingEvent {

    protected final UploadContext context;

    /**
     * Constructs the event which will reference the given context for
     * information.
     *
     * @param context the upload context
     */
    private AbstractStreamingEvent(UploadContext context) {
      this.context = context;
    }

    @Override
    public String getFileName() {
      return context.filename;
    }

    @Override
    public String getMimeType() {
      return context.contentType;
    }

    @Override
    public long getContentLength() {
      return context.dataContentLength;
    }

    @Override
    public long getBytesReceived() {
      return context.dataRead;
    }

  }

  /**
   * The progress event implementation.
   */
  private static class StreamingProgressEventImpl extends AbstractStreamingEvent
      implements StreamVariable.StreamingProgressEvent {

    /**
     * Constructs the event which will reference the given context for
     * information.
     *
     * @param context the upload context
     */
    private StreamingProgressEventImpl(UploadContext context) {
      super(context);
    }
  }

  /**
   * The error event implementation.
   */
  private static class StreamingErrorEventImpl extends AbstractStreamingEvent
      implements Html5StreamVariable.Html5StreamingErrorEvent {

    private final Exception ex;
    private Html5StreamVariable.UploadResponse response;

    /**
     * Constructs the event which will reference the given context for
     * information.
     *
     * @param context the upload context
     * @param ex the (optional) exception that caused the error
     */
    public StreamingErrorEventImpl(UploadContext context, Exception ex) {
      super(context);
      this.ex = ex;
    }

    @Override
    public Exception getException() {
      return ex;
    }

    @Override
    public void setResponse(Html5StreamVariable.UploadResponse response) {
      this.response = response;
    }

    /**
     * Returns the optional response set by the stream variable.
     *
     * @return the response
     */
    public Html5StreamVariable.UploadResponse getResponse() {
      return response;
    }
  }

  /**
   * The end event implementation.
   */
  private static class StreamingEndEventImpl extends AbstractStreamingEvent
      implements Html5StreamVariable.Html5StreamingEndEvent {

    private Html5StreamVariable.UploadResponse response;

    /**
     * Constructs the event which will reference the given context for
     * information.
     *
     * @param context the upload context
     */
    private StreamingEndEventImpl(UploadContext context) {
      super(context);
    }

    @Override
    public void setResponse(Html5StreamVariable.UploadResponse response) {
      this.response = response;
    }

    /**
     * Returns the optional response set by the stream variable.
     *
     * @return the response
     */
    public Html5StreamVariable.UploadResponse getResponse() {
      return response;
    }
  }

  /**
   * The started event implementation.
   */
  private static class StreamingStartEventImpl extends AbstractStreamingEvent
      implements Html5StreamVariable.Html5StreamingStartEvent {

    /**
     * Constructs the event which will reference the given context for
     * information.
     *
     * @param context the upload context
     */
    private StreamingStartEventImpl(UploadContext context) {
      super(context);
    }

    @Override
    public void disposeStreamVariable() {
      // no op
    }

    @Override
    public Collection<String> getParameterValues(String name) {
      return context.params.get(name);
    }

    @Override
    public String getParameterValue(String name) {
      Collection<String> values = getParameterValues(name);

      if (values == null || values.isEmpty()) {
        return null;
      }
      else {
        return values.iterator().next();
      }
    }
  }
}
