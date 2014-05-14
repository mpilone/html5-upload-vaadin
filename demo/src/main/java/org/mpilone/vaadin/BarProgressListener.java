
package org.mpilone.vaadin;

import com.vaadin.ui.ProgressBar;
import com.vaadin.ui.Upload;

/**
 * An upload progress listener that updates a progress bar.
 *
 * @author mpilone
 */
class BarProgressListener implements Upload.ProgressListener {
  private final ProgressBar bar;
  private final UploadLogger log;

  /**
   * Constructs the listener.
   *
   * @param bar the progress bar to update
   * @param log the log to write to
   */
  public BarProgressListener(ProgressBar bar, UploadLogger log) {
    this.bar = bar;
    this.log = log;
  }

  @Override
  public void updateProgress(long readBytes, long contentLength) {
    log.log("Received %d bytes of %d.", readBytes, contentLength);

    if (contentLength > 0) {
      float percent = (float) readBytes / (float) contentLength;
      bar.setValue(percent);
    }
  }

}
