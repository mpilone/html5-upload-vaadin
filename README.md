# Vaadin HTML5 Upload

Vaadin components for multiple HTML5 upload libraries including 
the [Plupload](http://www.plupload.com/) HTML5/Flash/Silverlight/HTML4 
and the [FineUploader](http://fineuploader.com) HTML5/HTML4 JavaScript 
libraries. The components attempt to implement an API that is extremely 
similar to the standard Vaadin Upload component so it should require 
relatively few changes to swap between the implementations.

The component implementations make use of a core support library which can be 
reused for other implementations of HTML5 uploders. The primary class in the 
library is a custom file request handler that makes use of 
[Apache Commons FileUpload](http://commons.apache.org/proper/commons-fileupload/) 
for fast, reliable multi-part upload handling.

The choice of which HTML5 upload component to use will come down to feature set, 
licensing concerns, and browser support. Currently the features exposed from 
each of the libraries is roughly equivalent; however licensing costs, 
communities, and support options vary greatly between the components.

**NOTE:** While this Vaadin component is free to use (GPLv3), you are 
responsible for properly licensing the underlying JavaScript libraries based on 
the license requirements for the specific component used. This Java component 
in no way grants or implies a license from the original JavaScript library 
authors.

# Core Support Library

* Custom file request handler based on Apache commons-fileupload.
* Interfaces and events for access to POST parameters and custom responses.
* Basic stream handling utility methods.
* In-memory or disk based buffering of chunks to allow for retries with an 
  arbitrary chunk size.

# Plupload

## Features
* Multiple, configurable client side runtimes (HTML5, Flash, Silverlight, and HTML4).
* Chunked uploading.
* Immediate or manual upload initiation.
* Client side maximum file size detection.
* Retry support on failed chunk upload.
* Modeled after the standard Upload component for server side compatibility.

## Limitations
* To be consistent with the existing Vaadin Upload component some 
  features are not exposed, such as the upload queue.
* Interrupting of HTML4 uploads with retries enabled may be slow as multiple 
  retries may need to abort before the entire upload is interrupted.

## Example Usage

    Plupload upload = new Plupload();
    upload.setMaxFileSize(500 * 1024 * 1024);
    upload.setButtonCaption("Upload File");
    upload.setReceiver(new MyReceiverImpl());

    // New features supported by HTML5 uploader.
    upload.setChunkSize(256 * 1024);    
    upload.setMaxRetries(2);
    
    upload.addStartedListener(new Plupload.StartedListener() {
      @Override
      public void uploadStarted(Plupload.StartedEvent evt) {
        // ...
      }
    });
    upload.addSucceededListener(new Plupload.SucceededListener() {
      @Override
      public void uploadSucceeded(Plupload.SucceededEvent evt) {
        // ...
      }
    });
    upload.addFailedListener(new Plupload.FailedListener() {
      @Override
      public void uploadFailed(Plupload.FailedEvent evt) {
        // ...
      }
    });

# FineUploader

## Features
* HTML5 client side runtime with fallback support for HTML4.
* Chunked uploading.
* Immediate or manual upload initiation.
* Client side maximum file size detection.
* Retry support on failed chunk upload.
* Modeled after the standard Upload component for server side compatibility.

## Limitations
* To be consistent with the existing Vaadin Upload component some 
  features are not exposed, such as the upload queue.
* Interrupting of HTML4 uploads with retries enabled may be slow as multiple 
  retries may need to abort before the entire upload is interrupted.

## Example Usage

    FineUploader upload = new FineUploader();
    upload.setMaxFileSize(500 * 1024 * 1024);
    upload.setButtonCaption("Upload File");
    upload.setReceiver(new MyReceiverImpl());

    // New features supported by HTML5 uploader.
    upload.setChunkSize(256 * 1024);    
    upload.setMaxRetries(2);
    
    upload.addStartedListener(new FineUploader.StartedListener() {
      @Override
      public void uploadStarted(FineUploader.StartedEvent evt) {
        // ...
      }
    });
    upload.addSucceededListener(new FineUploader.SucceededListener() {
      @Override
      public void uploadSucceeded(FineUploader.SucceededEvent evt) {
        // ...
      }
    });
    upload.addFailedListener(new FineUploader.FailedListener() {
      @Override
      public void uploadFailed(FineUploader.FailedEvent evt) {
        // ...
      }
    });

# Future Enhancements

* Exposing more features of the client side libraries while remaining 
  consistent with the Vaadin Upload component.
* Potentially exposing a completely new client side component that supports 
  all the features of the client side libraries and breaks from the Vaadin 
  Upload component.

