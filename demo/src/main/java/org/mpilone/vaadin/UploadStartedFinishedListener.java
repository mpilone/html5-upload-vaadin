
package org.mpilone.vaadin;

import org.mpilone.vaadin.upload.AbstractHtml5Upload;
import org.mpilone.vaadin.upload.plupload.Plupload;

/**
 * A simple listener that logs the started and finished events.
 *
 * @author mpilone
 */
public class UploadStartedFinishedListener implements
    AbstractHtml5Upload.FinishedListener, AbstractHtml5Upload.FailedListener,
    AbstractHtml5Upload.StartedListener, AbstractHtml5Upload.SucceededListener {

  private final UploadLogger log;
  private int startTime;

  public UploadStartedFinishedListener(UploadLogger log) {
    this.log = log;
  }

  @Override
  public void uploadStarted(Plupload.StartedEvent evt) {
    log.
        log("Upload of file %s started with content size %d.", evt.getFilename(),
            evt.getContentLength());

    evt.getComponent().setEnabled(false);

    this.startTime = (int) (System.currentTimeMillis() / 1000);
  }

  @Override
  public void uploadSucceeded(Plupload.SucceededEvent evt) {
    log.log("Upload of file %s succeeded with size %d.", evt.
        getFilename(), evt.getLength());
  }

  @Override
  public void uploadFailed(Plupload.FailedEvent evt) {
    log.log("Upload of file %s failed with size %d.", evt.
        getFilename(), evt.getLength());
  }

  @Override
  public void uploadFinished(Plupload.FinishedEvent evt) {
    AbstractHtml5Upload upload = (AbstractHtml5Upload) evt.getComponent();

    upload.setEnabled(true);

    String hash = null;
    int count = 0;
    if (upload.getReceiver() instanceof DemoReceiver) {
      DemoReceiver r = (DemoReceiver) upload.getReceiver();
      hash = r.getHash();
      count = r.getCount();
    }

    int now = (int) (System.currentTimeMillis() / 1000);
    int elapsed = now - startTime;

    double throughput = elapsed > 0 ? (count / (double) elapsed) * 8 / 1024
        / 1024 : Double.POSITIVE_INFINITY;

    log.log("Upload of file %s finished with reported size %d, "
        + "actual size %d, MD5 hash %s, and throughput %.2f Mb/s.", evt.
        getFilename(), evt.getLength(), count, hash, throughput);
  }

}
