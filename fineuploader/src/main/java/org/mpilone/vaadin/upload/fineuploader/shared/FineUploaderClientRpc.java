
package org.mpilone.vaadin.upload.fineuploader.shared;

import com.vaadin.shared.communication.ClientRpc;

/**
 * The remote procedure call interface which allows calls from the server side
 * to the client.
 *
 * @author mpilone
 */
public interface FineUploaderClientRpc extends ClientRpc {

  void submitUpload();

}
