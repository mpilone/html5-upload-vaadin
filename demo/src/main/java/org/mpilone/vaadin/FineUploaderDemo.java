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
    upload.setReceiver(new SlowDemoReceiver(this));
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
    upload.setReceiver(new SlowDemoReceiver(this));
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

    // Upload 7: Server initiated manual upload.
    upload = buildUpload();
    upload.setImmediate(false);
    upload.setButtonCaption(null);

    final FineUploader _upload7 = upload;
    btn = new Button("Server Side Submit", new Button.ClickListener() {
      @Override
      public void buttonClick(Button.ClickEvent event) {
        _upload7.submitUpload();
      }
    });
    addExample("Server Initiated Manual Upload", upload, btn);

    // Upload 8: Random failure submit.
    upload = buildUpload();
    upload.setButtonCaption("Randomly Fail");
    upload.setImmediate(true);
    upload.setMaxRetries(20);
    upload.setReceiver(new RandomFailureDemoReceiver(this));
    addExample("Immediate Submit and Random Failure", upload);

    // Upload 9: Random perminant failure submit.
    upload = buildUpload();
    upload.setButtonCaption("Randomly Fail");
    upload.setImmediate(true);
    upload.setReceiver(new RandomFailureDemoReceiver(this, true));
    addExample("Immediate Submit and Random Perminant Failure", upload);
  }

  private FineUploader buildUpload() {

    final FineUploader upload = new FineUploader();
    upload.setChunkSize(256 * 1024);
    upload.setMaxFileSize(500 * 1024 * 1024);
    upload.setMaxRetries(5);
    upload.setButtonCaption("Upload File");
    upload.setReceiver(new DemoReceiver(this));

    UploadStartedFinishedListener listener = new UploadStartedFinishedListener(
        this);
    upload.addSucceededListener(listener);
    upload.addFailedListener(listener);
    upload.addFinishedListener(listener);
    upload.addStartedListener(listener);

    return upload;
  }
}
