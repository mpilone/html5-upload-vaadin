
package org.mpilone.vaadin.upload;

import java.io.*;
import java.nio.ByteBuffer;

import com.vaadin.ui.Upload;

/**
 * An implementation of an {@link Html5Receiver} that implements two different
 * output streams: an in-memory buffering stream and a disk buffering stream.
 * The stream data is buffered to properly support retries and chunking. If
 * retries are not enabled, the output stream simply writes directly to the
 * original, delegate receiver's output stream.
 *
 * @author mpilone
 */
public class DefaultHtml5Receiver implements
    Html5Receiver {

  /**
   * The delegate receiver to create the underlying output stream.
   */
  private final Upload.Receiver delegate;

  /**
   * The maximum amount of memory to use in the in-memory output stream in
   * bytes. If the chunk size is larger than this value, the disk buffering
   * output stream will be used.
   */
  private final static int MAX_RETRY_MEMORY = 256 * 1024;

  /**
   * Constructs the receiver.
   *
   * @param delegate the delegate receiver to create the underlying output
   * stream
   */
  public DefaultHtml5Receiver(Upload.Receiver delegate) {
    this.delegate = delegate;
  }

  @Override
  public OutputStream receiveUpload(String filename, String mimeType) {
    return delegate.receiveUpload(filename, mimeType);
  }

  @Override
  public OutputStream receiveUpload(String filename, String mimeType,
      boolean retryEnabled, boolean chunkingEnabled,
      int chunkContentLength, int contentLength) {

    OutputStream outstream = receiveUpload(filename, mimeType);

    if (retryEnabled) {
      outstream = chunkContentLength <= MAX_RETRY_MEMORY ?
          new MemoryRetryableOutputStream(outstream) :
          new DiskRetryableOutputStream(outstream);
    }

    return outstream;
  }

  /**
   * An in-memory buffering output stream. The data will be stored in a byte
   * buffer and flushed to the delegate output stream when a chunk completes
   * successfully.
   */
  private static class MemoryRetryableOutputStream extends RetryableOutputStream {

    private final ByteBuffer buffer;
    private final OutputStream receiverOutstream;

    /**
     * Constructs the output stream which will buffer incoming data up to the
     * given capacity.
     *
     * @param delegate the delegate stream to write to
     */
    public MemoryRetryableOutputStream(OutputStream delegate) {
      this.buffer = ByteBuffer.allocate(MAX_RETRY_MEMORY);
      this.receiverOutstream = delegate;
    }

    @Override
    public void write(byte[] b) throws IOException {
      buffer.put(b);
    }

    @Override
    public void write(int b) throws IOException {
      buffer.put((byte) b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      buffer.put(b, off, len);
    }

    @Override
    public void chunkStart(int chunkIndex, int chunkCount) throws IOException {
      buffer.rewind();
    }

    @Override
    public void chunkEnd(int chunkIndex, int chunkCount) throws IOException {

      int available = buffer.position();
      buffer.rewind();

      Streams.copy(buffer, available, receiverOutstream);

      receiverOutstream.flush();
      buffer.rewind();
    }

    @Override
    public void close() throws IOException {
      super.close();
      receiverOutstream.close();
    }
  }

  /**
   * A file/disk buffering output stream. The data will be stored in a temporary
   * file and flushed to the delegate output stream when a chunk completes
   * successfully.
   */
  private static class DiskRetryableOutputStream extends RetryableOutputStream {

    protected final OutputStream receiverOutstream;
    private File tempFile;
    protected RandomAccessFile tempFileStream;

    public DiskRetryableOutputStream(OutputStream receiverOutstream) {
      this.receiverOutstream = receiverOutstream;
    }

    @Override
    public void chunkStart(int chunkIndex, int chunkCount) throws IOException {

      if (tempFileStream == null) {
//        System.out.println(
//            "** Creating new temporary file for the retryable output stream.");
        tempFile = File.createTempFile("upload_disk_retryable", null);
        tempFile.deleteOnExit();

        tempFileStream = new RandomAccessFile(tempFile, "rw");
      }

      // Truncate the file to prepare for the next chunk.
      tempFileStream.setLength(0);
    }

    @Override
    public void chunkEnd(int chunkIndex, int chunkCount) throws IOException {
      // Move back to the head of the file.
      tempFileStream.seek(0);

      // Copy all the data from the temporary file/buffer to the output stream.
      Streams.copy(tempFileStream, receiverOutstream);

      // Truncate the file for the next chunk.
      tempFileStream.setLength(0);
    }

    /**
     * Cleans up resources including closing the file output stream and deleting
     * the temporary file.
     *
     * @throws IOException if an error occurs
     */
    private void cleanUp() throws IOException {

      if (tempFileStream != null) {
        tempFileStream.close();
        tempFileStream = null;
      }

      if (tempFile != null) {
//        System.out.println(
//            "** Deleting temporary file for the retryable output stream.");
        tempFile.delete();
        tempFile = null;
      }
    }

    @Override
    public void close() throws IOException {
      super.close();
      receiverOutstream.close();

      cleanUp();
    }

    @Override
    public void write(int b) throws IOException {
      tempFileStream.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
      tempFileStream.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      tempFileStream.write(b, off, len);
    }
  }

}
