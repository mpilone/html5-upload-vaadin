package org.mpilone.vaadin.upload.fineuploader.shared;

import org.mpilone.vaadin.upload.fineuploader.FineUploader;

import com.vaadin.shared.ui.JavaScriptComponentState;

/**
 * Shared state for the {@link FineUploader} component.
  *
 * @author mpilone
 */
@SuppressWarnings("serial")
public class FineUploaderState extends JavaScriptComponentState {

  /**
   * The flag which indicates if a value changed in the component that will
   * require a complete rebuild of the client side component.
   */
  public boolean rebuild;

  /**
   * Page URL to where the files will be uploaded to.
   */
  public String url;

  /**
   * Maximum file size that the user can pick in bytes.
   */
  public long maxFileSize;

  /**
   * Enables you to chunk the file into smaller pieces for example if your PHP
   * backend has a max post size of 1MB you can chunk a 10MB file into 10
   * requests. To disable chunking, set to 0.
   */
  public int chunkSize;

  /**
   * Generate unique filenames when uploading. This will generate unique
   * filenames for the files so that they don't for example collide with
   * existing ones on the server.
   */
  public boolean uniqueNames;

  /**
   * The maximum number of times to retry a failed upload or chunk. To disable
   * retries, set to 0.
   */
  public int maxRetries;

  /**
   * The text displayed on the button that initiates the upload.
   */
  public String buttonCaption;

}
