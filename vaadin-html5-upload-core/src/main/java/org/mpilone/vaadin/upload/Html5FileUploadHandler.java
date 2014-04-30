package org.mpilone.vaadin.upload;

import static com.vaadin.server.communication.FileUploadHandler.DEFAULT_STREAMING_PROGRESS_EVENT_INTERVAL_MS;
import static java.util.Arrays.asList;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

import javax.servlet.http.*;

import org.apache.commons.fileupload.*;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import com.vaadin.server.*;
import com.vaadin.server.StreamVariable.StreamingErrorEvent;
import com.vaadin.server.communication.*;
import com.vaadin.ui.UI;


/**
 * A custom file upload request handler that generates
 * {@link Html5StreamVariable} events to allow the stream variable lower level
 * access to upload data such as form parameters and response messages which is
 * needed for HTML5, AJAX style uploaders such as FineUploader and Plupload.
 * This functionality could be rolled into the standard Vaadin
 * {@link FileUploadHandler} in the future.
 *
 * @author mpilone
 */
public class Html5FileUploadHandler implements RequestHandler {

  static final int MAX_UPLOAD_BUFFER_SIZE = 4 * 1024;
  private static final Charset UTF_8 = Charset.forName("UTF-8");
  private static final int CRLF_SIZE = 2;
  private static final int DASH_DASH_SIZE = 2;


  public static final String URL_PREFIX = "APP/HTML5_FILE_UPLOAD/";

  @Override
  public boolean handleRequest(VaadinSession session, VaadinRequest request,
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
    String[] parts = uppUri.split("/", 4);
    String uiId = parts[0];
    String connectorId = parts[1];
    String variableName = parts[2];

    // These are retrieved while session is locked
    ClientConnector source;
    StreamVariable streamVariable;

    session.lock();
    try {
      UI uI = session.getUIById(Integer.parseInt(uiId));
      UI.setCurrent(uI);

      streamVariable = uI.getConnectorTracker().getStreamVariable(
          connectorId, variableName);
      String secKey = uI.getConnectorTracker().getSeckey(streamVariable);
      if (!secKey.equals(parts[3])) {
        // TODO Should rethink error handling
        return true;
      }

      source = session.getCommunicationManager().getConnector(uI,
          connectorId);
    }
    finally {
      session.unlock();
    }

    String contentLengthHeader = request.
        getHeader(FileUploadBase.CONTENT_LENGTH);

    UploadContext context = new UploadContext();
    context.contentLength = contentLengthHeader != null ? Integer.parseInt(
        contentLengthHeader) : -1;
    context.dataContentLength = context.contentLength;
    context.contentType = request.getHeader(FileUploadBase.CONTENT_TYPE);
    context.request = request;
    context.response = response;
    context.session = session;
    context.source = source;
    context.streamVariable = streamVariable;
    context.params = new HashMap<>();

    // Copy over the parameters into our own map.
    for (Map.Entry<String, String[]> entry : context.request.getParameterMap().
        entrySet()) {
      Collection<String> values = new ArrayList<>(asList(entry.getValue()));
      context.params.put(entry.getKey(), values);
    }

    handleRequest(context);

    return true;
  }

  private HttpServletRequest asServletRequest(VaadinRequest request) {
    return ((VaadinServletRequest) request).getHttpServletRequest();
  }

  private void handleRequest(UploadContext context) {

    boolean isMultipart = ServletFileUpload.isMultipartContent(
        asServletRequest(context.request));

    if (isMultipart) {
      handleMultipartRequest(context);
    }
    else {
      throw new UnsupportedOperationException("Not supported. Client should "
          + "use multipart upload.");
    }
  }

  private void handleMultipartRequest(UploadContext context) {

    Html5StreamVariable.UploadResponse response = null;

    // Determine the size of the boundary.
    String contentDispositionHeader = context.request.getHeader(
        FileUploadBase.CONTENT_TYPE);
    int pos = contentDispositionHeader.indexOf("boundary=");
    String boundary = contentDispositionHeader.substring(pos + "boundary=".
        length());

    // Create a new file upload handler
    ServletFileUpload upload = new ServletFileUpload();

    try {
      // Parse the request
      FileItemIterator iter = upload.getItemIterator(asServletRequest(
          context.request));
      while (iter.hasNext()) {
        FileItemStream item = iter.next();
        String name = item.getFieldName();

        try (InputStream stream = item.openStream()) {
          if (item.isFormField()) {
            String value = Streams.asString(stream);
            if (!context.params.containsKey(name)) {
              context.params.put(name, new ArrayList<String>());
            }
            context.params.get(name).add(value);

            context.dataContentLength -=
                calcTotalItemSize(boundary, value, item.getHeaders());
          }
          else {
            context.filename = Streams.removePath(item.getName());
            context.contentType = item.getContentType();

            context.dataContentLength -=
                calcTotalItemSize(boundary, null, item.getHeaders());

            response = streamToReceiver(stream, context);
          }
        }
      }

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

      VaadinSession session = context.session;
      session.lock();
      try {
        session.getCommunicationManager()
            .handleConnectorRelatedException(context.source, e);
      }
      finally {
        session.unlock();
      }
    }
  }

  /**
   * Calculates the byte size of the file item part given the current boundary,
   * part value, and any part headers.
   *
   * @param boundary the multi-part boundary
   * @param value the value/data in the part
   * @param headers the part headers
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
      totalLength += headerName.getBytes(UTF_8).length + CRLF_SIZE;

      for (Iterator<String> iter2 = headers.getHeaders(headerName); iter2.
          hasNext();) {
        String headerValue = iter2.next();
        totalLength += headerValue.getBytes(UTF_8).length + CRLF_SIZE;
      }
    }

    // Blank line after headers.
    totalLength += CRLF_SIZE;

    // Item value (may not be set if this is the final file part)
    if (value != null) {
      totalLength += value.getBytes(UTF_8).length;
    }

    return totalLength;
  }

  private Html5StreamVariable.UploadResponse streamToReceiver(InputStream in,
      UploadContext context) throws UploadException {

    VaadinSession session = context.session;
    StreamVariable streamVariable = context.streamVariable;

    // Create the default response.
    Html5StreamVariable.UploadResponse response = null;

    OutputStream out = null;
    StreamingStartEventImpl startedEvent = new StreamingStartEventImpl(context);
    try {
      boolean listenProgress;
      session.lock();
      try {
        streamVariable.streamingStarted(startedEvent);
        out = streamVariable.getOutputStream();
        listenProgress = streamVariable.listenProgress();
      }
      finally {
        session.unlock();
      }

      if (out == null) {
        // No output stream to write to.
        throw new NoOutputStreamException();
      }

      if (in == null) {
        // No input stream to read from.
        throw new NoInputStreamException();
      }

      final byte buffer[] = new byte[MAX_UPLOAD_BUFFER_SIZE];
      int bytesRead;
      long lastStreamingEvent = 0;
      while ((bytesRead = in.read(buffer)) > 0) {
        out.write(buffer, 0, bytesRead);
        context.dataRead += bytesRead;

        if (listenProgress) {
          long now = System.currentTimeMillis();
          // to avoid excessive session locking and event storms,
          // events are sent in intervals, or at the end of the file.
          if (lastStreamingEvent + getProgressEventInterval() <= now
              || bytesRead <= 0) {
            lastStreamingEvent = now;
            // update progress if listener set and contentLength received
            session.lock();
            try {
              StreamingProgressEventImpl progressEvent =
                  new StreamingProgressEventImpl(context);
              streamVariable.onProgress(progressEvent);
            }
            finally {
              session.unlock();
            }
          }
        }

        if (streamVariable.isInterrupted()) {
          throw new FileUploadHandler.UploadInterruptedException();
        }
      }

      // upload successful
      out.close();
      StreamingEndEventImpl event = new StreamingEndEventImpl(context);
      session.lock();
      try {
        streamVariable.streamingFinished(event);
      }
      finally {
        session.unlock();
      }

      response = event.getResponse();
    }
    catch (FileUploadHandler.UploadInterruptedException e) {
      // Download interrupted by application code. Relay the error to the
      // stream variable and use the custom response if set.
      //
      // Note, we are not throwing interrupted exception forward as it is
      // not a terminal level error like all other exception.
      Streams.tryClose(out);
      StreamingErrorEventImpl event = new StreamingErrorEventImpl(context, e);
      session.lock();
      try {
        streamVariable.streamingFailed(event);
      }
      finally {
        session.unlock();
      }

      response = event.getResponse();
    }
    catch (Exception e) {
      // Download interrupted by an unexpected error. Replay the error to
      // the stream variable and raise an exception.
      Streams.tryClose(out);
      session.lock();
      try {
        StreamingErrorEvent event = new StreamingErrorEventImpl(context, e);
        streamVariable.streamingFailed(event);

        // throw exception for terminal to be handled (to be passed to
        // terminalErrorHandler)
        throw new UploadException(e);
      }
      finally {
        session.unlock();
      }
    }

    return response;
  }

  /**
   * To prevent event storming, streaming progress events are sent in this
   * interval rather than every time the buffer is filled. This fixes #13155. To
   * adjust this value override the method, and register your own handler in
   * VaadinService.createRequestHandlers(). The default is 500ms, and setting it
   * to 0 effectively restores the old behavior.
   */
  protected int getProgressEventInterval() {
    return DEFAULT_STREAMING_PROGRESS_EVENT_INTERVAL_MS;
  }

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

  private static class UploadContext {

    public VaadinRequest request;
    public VaadinResponse response;
    public VaadinSession session;
    public String contentType;
    public int contentLength = -1;
    public int dataContentLength = -1;
    public int dataRead = 0;
    public String filename;
    public StreamVariable streamVariable;
    public ClientConnector source;
    public Map<String, Collection<String>> params;
  }

  private static abstract class AbstractStreamingEvent implements
      StreamVariable.StreamingEvent {

    protected final UploadContext context;

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

  private static class StreamingProgressEventImpl extends AbstractStreamingEvent
      implements StreamVariable.StreamingProgressEvent {

    private StreamingProgressEventImpl(UploadContext context) {
      super(context);
    }
  }

  private static class StreamingErrorEventImpl extends AbstractStreamingEvent
      implements Html5StreamVariable.Html5StreamingErrorEvent {

    private final Exception ex;
    private Html5StreamVariable.UploadResponse response;

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

    public Html5StreamVariable.UploadResponse getResponse() {
      return response;
    }
  }

  private static class StreamingEndEventImpl extends AbstractStreamingEvent
      implements Html5StreamVariable.Html5StreamingEndEvent {

    private Html5StreamVariable.UploadResponse response;

    private StreamingEndEventImpl(
        UploadContext context) {
      super(context);
    }

    @Override
    public void setResponse(Html5StreamVariable.UploadResponse response) {
      this.response = response;
    }

    public Html5StreamVariable.UploadResponse getResponse() {
      return response;
    }
  }

  private static class StreamingStartEventImpl extends AbstractStreamingEvent
      implements Html5StreamVariable.Html5StreamingStartEvent {

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
