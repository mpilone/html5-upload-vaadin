package org.mpilone.vaadin.upload.fineuploader.shared;

import com.vaadin.shared.communication.ServerRpc;

/**
 * The remote procedure call interface which allows calls from the client side
 * to the server. For the most part these methods map to the events generated by
 * the FineUploader JavaScript component.
 *
 * @author mpilone
 */
public interface FineUploaderServerRpc extends ServerRpc {

  void onError(Integer id, String name, String errorReason);

  void onComplete(int id, String name);

  void onInit(String runtime);

  void onProgress(int id, String name, int uploadedBytes, int totalBytes);
}
