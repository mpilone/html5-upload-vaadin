
package org.mpilone.vaadin.upload;

import java.util.Collection;

import com.vaadin.server.StreamVariable;

/**
 *
 * @author mpilone
 */
public interface Html5StreamVariable extends StreamVariable {

  class UploadResponse {

    private int statusCode;
    private String contentType;
    private String content;

    public UploadResponse(int statusCode, String contentType, String content) {
      this.statusCode = statusCode;
      this.contentType = contentType;
      this.content = content;
    }

    public String getContent() {
      return content;
    }

    public String getContentType() {
      return contentType;
    }

    public int getStatusCode() {
      return statusCode;
    }
  }

  interface Html5StreamingStartEvent extends StreamingStartEvent {

    String getParameterValue(String name);

    Collection<String> getParameterValues(String name);
  }

  interface Html5StreamingEndEvent extends StreamingEndEvent {
    void setResponse(UploadResponse response);
  }

  interface Html5StreamingErrorEvent extends StreamingErrorEvent {

    void setResponse(UploadResponse response);
  }
}
