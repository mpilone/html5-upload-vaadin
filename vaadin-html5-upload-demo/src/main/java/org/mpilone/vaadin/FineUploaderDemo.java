package org.mpilone.vaadin;

import static java.lang.String.format;
import static org.mpilone.vaadin.StyleConstants.FULL_WIDTH;

import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.util.Date;

import org.mpilone.vaadin.upload.fineuploader.FineUploader;

import com.vaadin.shared.ui.label.ContentMode;
import com.vaadin.ui.*;

/**
 * Demo for the FineUploader Vaadin component.
 *
 * @author mpilone
 */
public class FineUploaderDemo extends HorizontalLayout {
  private TextArea logTxt;

  private final VerticalLayout leftColumnLayout;

  public FineUploaderDemo() {
    //setPollInterval(3000);
    setWidth(FULL_WIDTH);

    HorizontalLayout contentLayout = this;
    contentLayout.setMargin(true);
    contentLayout.setSpacing(true);
    contentLayout.setWidth(FULL_WIDTH);

    leftColumnLayout = new VerticalLayout();
    leftColumnLayout.setSpacing(true);
    contentLayout.addComponent(leftColumnLayout);

    // Upload 1: Manual submit button.
    FineUploader upload = buildFineUploader();
    addExample("Manual Submit", upload);

    // Upload 2: Immediate submit.
    upload = buildFineUploader();
    upload.setImmediate(true);
    upload.setButtonCaption("Upload Now");
    addExample("Immediate Submit", upload);

    // Upload 3: Immediate submit forced slow.
    upload = buildFineUploader();
    upload.setButtonCaption("Slow it Down");
    upload.setReceiver(new DemoReceiver(true));
    upload.setImmediate(true);
    final FineUploader _upload4 = upload;

    Button btn = new Button("Interrupt", new Button.ClickListener() {
      @Override
      public void buttonClick(Button.ClickEvent event) {
        _upload4.interruptUpload();
      }
    });
    addExample("Immediate Submit Forced Slow", upload, btn);

    // Upload 4: Manual submit forced slow.
    upload = buildFineUploader();
    upload.setButtonCaption("Slow and Manual");
    upload.setReceiver(new DemoReceiver(true));
    final FineUploader _upload6 = upload;

    btn = new Button("Interrupt", new Button.ClickListener() {
      @Override
      public void buttonClick(Button.ClickEvent event) {
        _upload6.interruptUpload();
      }
    });
    addExample("Manual Submit Forced Slow", upload, btn);

    // Upload 5: Immediate submit with max size 1 MiB.
    upload = buildFineUploader();
    upload.setImmediate(true);
    upload.setButtonCaption("Upload w/Max");
    upload.setMaxFileSize(1024 * 1024);
    addExample("Immediate Submit with Max 1 MiB", upload);

    // Upload 6: Immediate submit with no chunking.
    upload = buildFineUploader();
    upload.setImmediate(true);
    upload.setButtonCaption("Upload w/o Chunking");
    upload.setChunkSize(0);
    addExample("Immediate Submit with out Chunking", upload);

    // Right column (log area)
    VerticalLayout rightColumnLayout = new VerticalLayout();
    rightColumnLayout.setSpacing(true);
    contentLayout.addComponent(rightColumnLayout);

    Label lbl = new Label("<h2>Upload Log</h2>", ContentMode.HTML);
    rightColumnLayout.addComponent(lbl);

    logTxt = new TextArea();
    logTxt.setRows(35);
    logTxt.setWidth(FULL_WIDTH);
    rightColumnLayout.addComponent(logTxt);

    btn = new Button("Clear Log", new Button.ClickListener() {
      @Override
      public void buttonClick(Button.ClickEvent event) {
        logTxt.setValue("");
      }
    });
    rightColumnLayout.addComponent(btn);
  }

  private void addExample(String title, FineUploader upload, Component... comps) {
    ProgressBar progressBar = new ProgressBar();
    upload.addProgressListener(new BarProgressListener(progressBar));

    Label lbl = new Label("<h2>" + title + "</h2>", ContentMode.HTML);
    leftColumnLayout.addComponent(lbl);

    HorizontalLayout rowLayout = new HorizontalLayout();
    rowLayout.setSpacing(true);
    leftColumnLayout.addComponent(rowLayout);

    rowLayout.addComponent(upload);
    rowLayout.addComponent(progressBar);

    if (comps != null) {
      for (Component c : comps) {
        rowLayout.addComponent(c);
      }
    }
  }

  private void log(String msg, Object... args) {

    if (args != null && args.length > 0) {
      msg = format(msg, args);
    }

    String value = logTxt.getValue();
    value = format("%s\n[%s] %s", value, new Date(), msg);
    logTxt.setValue(value);
    logTxt.setCursorPosition(value.length() - 1);
  }

  private FineUploader buildFineUploader() {

    final FineUploader upload = new FineUploader();
    upload.setChunkSize(256 * 1024);
    upload.setMaxFileSize(500 * 1024 * 1024);
    upload.setMaxRetries(2);
    upload.setButtonCaption("Upload File");
    upload.setReceiver(new DemoReceiver(false));

    upload.addStartedListener(new FineUploader.StartedListener() {
      @Override
      public void uploadStarted(FineUploader.StartedEvent evt) {
        log("Upload of file %s started with content size %d.",
            evt.getFilename(), evt.getContentLength());

        upload.setEnabled(false);
      }
    });
    upload.addSucceededListener(new FineUploader.SucceededListener() {
      @Override
      public void uploadSucceeded(FineUploader.SucceededEvent evt) {
        log("Upload of file %s succeeded with size %d.", evt.
            getFilename(), evt.getLength());
      }
    });
    upload.addFailedListener(new FineUploader.FailedListener() {
      @Override
      public void uploadFailed(FineUploader.FailedEvent evt) {
        log("Upload of file %s failed with size %d.", evt.
            getFilename(), evt.getLength());
      }
    });
    upload.addFinishedListener(new FineUploader.FinishedListener() {
      @Override
      public void uploadFinished(FineUploader.FinishedEvent evt) {
        upload.setEnabled(true);

        String hash = null;
        int count = 0;
        if (upload.getReceiver() instanceof DemoReceiver) {
          CountingDigestOutputStream o = ((DemoReceiver) upload.getReceiver()).
              getOutputStream();

          if (o != null) {
            hash = o.getHash();
            count = o.getCount();
            o.reset();
          }
        }

        log("Upload of file %s finished with reported size %d, "
            + "actual size %d, and MD5 hash %s.", evt.getFilename(),
            evt.getLength(), count, hash);
      }
    });

    return upload;
  }

  private class BarProgressListener implements
      Upload.ProgressListener {

    private final ProgressBar bar;

    public BarProgressListener(ProgressBar bar) {
      this.bar = bar;
    }

    @Override
    public void updateProgress(long readBytes, long contentLength) {
      if (readBytes % 2048 == 0 || readBytes == contentLength) {
        log("Read %d bytes of %d.", readBytes, contentLength);
      }

      if (contentLength > 0) {
        float percent = (float) readBytes / (float) contentLength;
        bar.setValue(percent);
      }
    }
  }

  /**
   * A stream that counts the number of bytes writing and can generate an MD5
   * hash of the data written.
   */
  private class CountingDigestOutputStream extends OutputStream {

    private int count = 0;
    private MessageDigest md;
    private boolean closed = false;

    public CountingDigestOutputStream() {
      reset();
    }

    @Override
    public void close() throws IOException {
      super.close();

      closed = true;
    }

    @Override
    public void write(int b) throws IOException {
      if (closed) {
        throw new IOException("Output stream closed.");
      }

      count++;
      md.update((byte) b);
    }

    public void reset() {
      count = 0;

      try {
        md = MessageDigest.getInstance("MD5");
      }
      catch (NoSuchAlgorithmException ex) {
        throw new RuntimeException("Unable to create MD5 digest.", ex);
      }
    }

    public int getCount() {
      return count;
    }

    public String getHash() {
      byte[] hash = md.digest();

      BigInteger bigInt = new BigInteger(1, hash);
      String hashText = bigInt.toString(16);
      while (hashText.length() < 32) {
        hashText = "0" + hashText;
      }

      return hashText;
    }
  }

  /**
   * A stream that sleeps periodically to slow down writes.
   */
  private class SlowOutputStream extends CountingDigestOutputStream {

    @Override
    public void write(int b) throws IOException {

      if (getCount() % (1024 * 10) == 0) {
        try {
          Thread.sleep(10);
        }
        catch (InterruptedException ex) {
          // ignore
        }
      }

      super.write(b);
    }

  }

  /**
   * Receiver that returns a pre-configured output stream.
   */
  private class DemoReceiver implements Upload.Receiver {

    private final boolean slow;
    private CountingDigestOutputStream outstream;

    public DemoReceiver(boolean slow) {
      this.slow = slow;
      this.outstream = null;
    }

    @Override
    public OutputStream receiveUpload(String filename, String mimeType) {
      log("Creating receiver output stream for file %s and mime-type %s.",
          filename, mimeType);

      this.outstream = slow ? new SlowOutputStream() :
          new CountingDigestOutputStream();

      return this.outstream;
    }

    public CountingDigestOutputStream getOutputStream() {
      return outstream;
    }
  }


}
