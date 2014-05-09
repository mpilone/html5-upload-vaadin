
package org.mpilone.vaadin.upload;

import java.io.IOException;
import java.io.OutputStream;

import com.vaadin.ui.Upload;

/**
 * An extension of the {@link Upload.Receiver} interface to support HTML5/Ajax
 * features such as chunking and retries.
 *
 * @author mpilone
 */
public interface Html5Receiver extends Upload.Receiver {

  /**
   * Called when an upload is started. The receiver must create an appropriate
   * output stream based on the parameters provided. If {@code retryEnabled} is
   * true, a {@link RetryableOutputStream} should be returned to prevent data
   * corruption or duplication in the event that a retry is attempted by the
   * client. The {@code chunkContentLength} can be used to determine if
   * in-memory buffering or disk buffering should be used to buffer data until
   * the chunk completes successfully. Because most HTML5 uploaders will fall
   * back to HTML4 in an older browser, it is possible that the chunk content
   * length could be the entire length of the file even if a smaller chunk size
   * is configured.
   *
   * @param filename the name of the file being uploaded
   * @param mimeType the content type of the file
   * @param retryEnabled true if retries are enabled in the uploader
   * @param chunkingEnabled true if chunking is enabled in the uploader (but may
   * not be supported by the client)
   * @param chunkContentLength the length of the first chunk detected
   * @param contentLength the (estimated) total length of the file
   *
   * @return the output stream to write to
   */
  public OutputStream receiveUpload(String filename, String mimeType,
      boolean retryEnabled, boolean chunkingEnabled, int chunkContentLength,
      int contentLength);

  /**
   * An output stream that can retry writing a chunk in the event that the chunk
   * fails to be completely written. Before writing a chunk, the upload handler
   * will call {@link #chunkStart(int, int) } and after the chunk is completely
   * written the upload handler will call {@link #chunkEnd(int, int) }. If the
   * chunk fails to be completely written, the chunkEnd call will not occur and
   * another chunkStart call will be called. The output stream is responsible
   * for discarding any data written since the last chunkEnd call and restarting
   * the chunk.
   *
   * @author mpilone
   */
  abstract class RetryableOutputStream extends OutputStream {

    /**
     * Called when the upload of a chunk is starting. Any data written since the
     * last chunkEnd call should be discarded and assumed to be invalid.
     *
     * @param chunkIndex the index of the chunk that is starting (0 based)
     * @param chunkCount the total count of chunks to expect
     *
     * @throws IOException if there is an error writing
     */
    public abstract void chunkStart(int chunkIndex, int chunkCount)
        throws IOException;

    /**
     * Called when the upload of a chunk has completed successfully. Any data
     * written since the last chunkStart call should be assumed valid and
     * potentially written to final storage.
     *
     * @param chunkIndex the index of the chunk that is starting (0 based)
     * @param chunkCount the total count of chunks to expect
     *
     * @throws IOException if there is an error writing
     */
    public abstract void chunkEnd(int chunkIndex, int chunkCount)
        throws IOException;
  }
}
