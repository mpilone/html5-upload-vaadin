package org.mpilone.vaadin;

import org.mpilone.vaadin.upload.fineuploader.FineUploader;

import com.vaadin.ui.*;

/**
 * Demo for the FineUploader Vaadin component.
 *
 * @author mpilone
 */
public class FineUploaderDemo extends AbstractUploadDemo {

  public FineUploaderDemo() {

    // Upload 1: Manual submit button.
    FineUploader upload = buildUpload();
    addExample("Manual Submit", upload);

    // Upload 2: Immediate submit.
    upload = buildUpload();
    upload.setImmediate(true);
    upload.setButtonCaption("Upload Now");
    addExample("Immediate Submit", upload);

    // Upload 3: Immediate submit forced slow.
    upload = buildUpload();
    upload.setButtonCaption("Slow it Down");
    upload.setReceiver(new SlowHtml5DemoReceiver(this));
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
    upload = buildUpload();
    upload.setButtonCaption("Slow and Manual");
    upload.setReceiver(new SlowHtml5DemoReceiver(this));
    final FineUploader _upload6 = upload;

    btn = new Button("Interrupt", new Button.ClickListener() {
      @Override
      public void buttonClick(Button.ClickEvent event) {
        _upload6.interruptUpload();
      }
    });
    addExample("Manual Submit Forced Slow", upload, btn);

    // Upload 5: Immediate submit with max size 1 MiB.
    upload = buildUpload();
    upload.setImmediate(true);
    upload.setButtonCaption("Upload w/Max");
    upload.setMaxFileSize(1024 * 1024);
    addExample("Immediate Submit with Max 1 MiB", upload);

    // Upload 6: Immediate submit with no chunking.
    upload = buildUpload();
    upload.setImmediate(true);
    upload.setButtonCaption("Upload w/o Chunking");
    upload.setChunkSize(0);
    addExample("Immediate Submit with out Chunking", upload);

  }


  private FineUploader buildUpload() {

    final FineUploader upload = new FineUploader();
    upload.setChunkSize(256 * 1024);
    upload.setMaxFileSize(500 * 1024 * 1024);
    upload.setMaxRetries(2);
    upload.setButtonCaption("Upload File");
    upload.setReceiver(new DemoReceiver(this));

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
          DemoReceiver r = (DemoReceiver) upload.getReceiver();
          hash = r.getHash();
          count = r.getCount();
        }

        log("Upload of file %s finished with reported size %d, "
            + "actual size %d, and MD5 hash %s.", evt.getFilename(),
            evt.getLength(), count, hash);
      }
    });

    return upload;
  }
}
