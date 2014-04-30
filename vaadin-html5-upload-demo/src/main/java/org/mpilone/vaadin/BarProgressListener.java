
package org.mpilone.vaadin;

import com.vaadin.ui.ProgressBar;
import com.vaadin.ui.Upload;

/**
 *
 * @author mpilone
 */
class BarProgressListener implements Upload.ProgressListener {
  private final ProgressBar bar;
  private final UploadLogger log;

  public BarProgressListener(ProgressBar bar, UploadLogger log) {
    this.bar = bar;
    this.log = log;
  }

  @Override
  public void updateProgress(long readBytes, long contentLength) {
    if (readBytes % 2048 == 0 || readBytes == contentLength) {
      log.log("Read %d bytes of %d.", readBytes, contentLength);
    }
    if (contentLength > 0) {
      float percent = (float) readBytes / (float) contentLength;
      bar.setValue(percent);
    }
  }

}
