package org.mpilone.vaadin;

import org.mpilone.vaadin.upload.plupload.Plupload;

import com.vaadin.ui.*;

/**
 * Demo for the Plupload Vaadin component.
 *
 * @author mpilone
 */
public class PluploadDemo extends AbstractUploadDemo {

  public PluploadDemo() {

    // Upload 1: Manual submit button.
    Plupload upload = buildUpload();
    addExample("Manual Submit", upload);

    // Upload 2: Immediate submit.
    upload = buildUpload();
    upload.setImmediate(true);
    upload.setButtonCaption("Upload Now");
    addExample("Immediate Submit", upload);

    // Upload 3: Manual submit using flash.
    upload = buildUpload();
    upload.setRuntimes(Plupload.Runtime.FLASH);
    upload.setImmediate(true);
    upload.setButtonCaption("Flash is Fun");
    addExample("Immediate Submit using Flash", upload);

    // Upload 4: Manual submit using html4.
    upload = buildUpload();
    upload.setRuntimes(Plupload.Runtime.HTML4);
    upload.setButtonCaption("HTML4 is so Old");
    addExample("Manual Submit using HTML4", upload);

    // Upload 4: Immediate submit forced slow.
    upload = buildUpload();
    upload.setButtonCaption("Slow it Down");
    upload.setReceiver(new SlowHtml5DemoReceiver(this));
    upload.setImmediate(true);
    final Plupload _upload4 = upload;

    Button btn = new Button("Interrupt", new Button.ClickListener() {
      @Override
      public void buttonClick(Button.ClickEvent event) {
        _upload4.interruptUpload();
      }
    });
    addExample("Immediate Submit Forced Slow", upload, btn);

    // Upload 5: Immediate submit HTML4 forced slow.
    upload = buildUpload();
    upload.setRuntimes(Plupload.Runtime.HTML4);
    upload.setButtonCaption("Slow and Old");
    upload.setReceiver(new SlowHtml5DemoReceiver(this));
    upload.setImmediate(true);
    final Plupload _upload5 = upload;

    btn = new Button("Interrupt", new Button.ClickListener() {
      @Override
      public void buttonClick(Button.ClickEvent event) {
        _upload5.interruptUpload();
      }
    });
    btn.setImmediate(true);
    addExample("Immediate Submit Forced Slow using HTML4", upload, btn);

    // Upload 6: Manual submit HTML4 forced slow.
    upload = buildUpload();
    upload.setRuntimes(Plupload.Runtime.HTML4);
    upload.setButtonCaption("Slow and Manual");
    upload.setReceiver(new SlowHtml5DemoReceiver(this));
    final Plupload _upload6 = upload;

    btn = new Button("Interrupt", new Button.ClickListener() {
      @Override
      public void buttonClick(Button.ClickEvent event) {
        _upload6.interruptUpload();
      }
    });
    addExample("Manual Submit Forced Slow using HTML4", upload, btn);

    // Upload 7: Immediate submit with max size 1 MiB.
    upload = buildUpload();
    upload.setImmediate(true);
    upload.setButtonCaption("Upload w/Max");
    upload.setMaxFileSize(1024 * 1024);
    addExample("Immediate Submit with Max 1 MiB", upload);

    // Upload 8: Server initiated manual upload.
    upload = buildUpload();
    upload.setImmediate(false);
    upload.setButtonCaption(null);

    final Plupload _upload8 = upload;
    btn = new Button("Server Side Submit", new Button.ClickListener() {
      @Override
      public void buttonClick(Button.ClickEvent event) {
        _upload8.submitUpload();
      }
    });
    addExample("Server Initiated Manual Upload", upload, btn);
  }

  private Plupload buildUpload() {

    final Plupload upload = new Plupload();
    upload.setChunkSize(256 * 1024);
    upload.setMaxFileSize(500 * 1024 * 1024);
    upload.setMaxRetries(2);
    upload.setButtonCaption("Upload File");
    upload.setReceiver(new DemoReceiver(this));

    UploadStartedFinishedListener listener = new UploadStartedFinishedListener(
        this);
    upload.addSucceededListener(listener);
    upload.addFailedListener(listener);
    upload.addFinishedListener(listener);
    upload.addStartedListener(listener);
    upload.addStartedListener(new Plupload.StartedListener() {
      @Override
      public void uploadStarted(Plupload.StartedEvent evt) {
        log("Using runtime %s.", upload.getSelectedRuntime());
      }
    });

    return upload;
  }

}
