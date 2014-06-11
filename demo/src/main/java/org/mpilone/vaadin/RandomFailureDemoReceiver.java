package org.mpilone.vaadin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.mpilone.vaadin.upload.Html5Receiver;

/**
 * A receiver used in the demos that will randomly fail during a write operation
 * to force retry testing and possible failed uploads.
 *
 * @author mpilone
 */
public class RandomFailureDemoReceiver extends DemoReceiver implements
    Html5Receiver {

  private final boolean perminantFailureEnabled;

  public RandomFailureDemoReceiver(UploadLogger log) {
    this(log, false);
  }

  public RandomFailureDemoReceiver(UploadLogger log,
      boolean perminantFailureEnabled) {
    super(log);

    this.perminantFailureEnabled = perminantFailureEnabled;
  }

  @Override
  public OutputStream receiveUpload(String filename, String mimeType,
      boolean retryEnabled, boolean chunkingEnabled, int chunkContentLength,
      int contentLength) {
    return new RandomFailureOutputStream(super.receiveUpload(filename, mimeType));
  }

  /**
   * A stream that sleeps periodically to slow down writes.
   */
  private class RandomFailureOutputStream extends Html5Receiver.RetryableOutputStream {

    private final OutputStream delegate;
    private ByteArrayOutputStream bufOutstream;
    private boolean inFailureState;

    public RandomFailureOutputStream(OutputStream delegate) {
      this.delegate = delegate;
    }

    @Override
    public void write(byte[] b) throws IOException {
      bufOutstream.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      bufOutstream.write(b, off, len);
    }

    @Override
    public void write(int b) throws IOException {
      bufOutstream.write(b);
    }

//    private void maybeFail() throws IOException {
//
//      if (countSinceLastFailure > (256 * 1024) && Math.random() > 0.98) {
//        countSinceLastFailure = 0;
//        log.log("Simulating an IO error in the receiver.");
//
//        throw new IOException("Simulating an IO error.");
//      }
//    }

    @Override
    public void chunkStart(int chunkIndex, int chunkCount) throws IOException {
      log.log("Chunk start: index %d, count %d", chunkIndex, chunkCount);

      if (delegate instanceof Html5Receiver.RetryableOutputStream) {
        ((Html5Receiver.RetryableOutputStream) delegate).chunkStart(chunkIndex,
            chunkCount);
      }

      bufOutstream = new ByteArrayOutputStream();
    }

    @Override
    public void chunkEnd(int chunkIndex, int chunkCount) throws IOException {

      if (Math.random() > 0.9 || inFailureState) {
        log.log("Simulating an IO error in the receiver.");

        inFailureState = perminantFailureEnabled;

        throw new IOException("Simulating an IO error.");
      }

      log.log("Chunk end: index %d, count %d", chunkIndex, chunkCount);

      bufOutstream.close();
      delegate.write(bufOutstream.toByteArray());
      bufOutstream = null;

      if (delegate instanceof Html5Receiver.RetryableOutputStream) {
        ((Html5Receiver.RetryableOutputStream) delegate).chunkEnd(chunkIndex,
            chunkCount);
      }
    }
  }
}
