
package org.mpilone.vaadin;

import java.io.OutputStream;

import com.vaadin.ui.Upload;

/**
 * Receiver that returns a pre-configured output stream.
 */
class DemoReceiver implements Upload.Receiver {
  protected CountingDigestOutputStream outstream;
  protected final UploadLogger log;

  public DemoReceiver(UploadLogger log) {
    this.log = log;
    this.outstream = null;
  }

  @Override
  public OutputStream receiveUpload(String filename, String mimeType) {
    log.log("Creating receiver output stream for file %s and mime-type %s.",
        filename, mimeType);
    this.outstream = new CountingDigestOutputStream();

    return this.outstream;
  }

  public String getHash() {
    return outstream != null ? outstream.getHash() : null;
  }

  public int getCount() {
    return outstream != null ? outstream.getCount() : -1;
  }

}
