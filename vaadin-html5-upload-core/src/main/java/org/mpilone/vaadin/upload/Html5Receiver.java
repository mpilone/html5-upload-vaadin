
package org.mpilone.vaadin.upload;

import java.io.IOException;
import java.io.OutputStream;

import com.vaadin.ui.Upload;

/**
 *
 * @author mpilone
 */
public interface Html5Receiver extends Upload.Receiver {

  public OutputStream receiveUpload(String filename, String mimeType,
      boolean retryEnabled, boolean chunkingEnabled, int chunkContentLength,
      int contentLength);

  /**
   *
   * @author mpilone
   */
  abstract class RetryableOutputStream extends OutputStream {

    public abstract void chunkStart(int chunkIndex, int chunkCount)
        throws IOException;

    public abstract void chunkEnd(int chunkIndex, int chunkCount)
        throws IOException;
  }
}
