
package org.mpilone.vaadin;

import java.io.IOException;
import java.io.OutputStream;

import org.mpilone.vaadin.upload.Html5Receiver;

/**
 *
 * @author mpilone
 */
public class SlowDemoReceiver extends DemoReceiver implements Html5Receiver {

  public SlowDemoReceiver(UploadLogger log) {
    super(log);
  }

  @Override
  public OutputStream receiveUpload(String filename, String mimeType,
      boolean retryEnabled, boolean chunkingEnabled, int chunkContentLength,
      int contentLength) {
    return new SlowOutputStream(super.receiveUpload(filename, mimeType));
  }

/**
 * A stream that sleeps periodically to slow down writes.
 */
  private class SlowOutputStream extends Html5Receiver.RetryableOutputStream {

    private int countSinceLastSleep = 0;
    private final OutputStream delegate;

    public SlowOutputStream(OutputStream delegate) {
      this.delegate = delegate;
    }

  @Override
  public void write(byte[] b) throws IOException {
    delegate.write(b);

    countSinceLastSleep += b.length;
    maybeSleep();
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    delegate.write(b, off, len);

    countSinceLastSleep += len;
    maybeSleep();
  }

  @Override
  public void write(int b) throws IOException {
    delegate.write(b);

    countSinceLastSleep++;
    maybeSleep();
  }

  private void maybeSleep() {

    if (countSinceLastSleep > (1024 * 5)) {
      try {
        Thread.sleep(10);
      }
      catch (InterruptedException ex) {
        // ignore
      }

      countSinceLastSleep = 0;
    }
  }

    @Override
    public void chunkStart(int chunkIndex, int chunkCount) throws IOException {
      // no op
    }

    @Override
    public void chunkEnd(int chunkIndex, int chunkCount) throws IOException {
      // no op
    }
  }
}
