
package org.mpilone.vaadin.upload;

import java.io.*;
import java.nio.ByteBuffer;


/**
 * Utility methods for working with streams.
 *
 * @author mpilone
 */
public class Streams {

  public static void copy(InputStream instream, OutputStream outstream) throws
      IOException {
    byte[] buf = new byte[Html5FileUploadHandler.MAX_UPLOAD_BUFFER_SIZE];

    int read;
    while ((read = instream.read(buf)) != -1) {
      outstream.write(buf, 0, read);
    }
  }

  static void copy(ByteBuffer buffer, int length, OutputStream receiverOutstream)
      throws IOException {

    byte[] buf = new byte[Html5FileUploadHandler.MAX_UPLOAD_BUFFER_SIZE];
    while (length > 0) {
      int len = Math.min(buf.length, length);
      length -= len;

      buffer.get(buf, 0, len);
      receiverOutstream.write(buf, 0, len);
    }
  }

  /**
   * Removes any possible path information from the filename and returns the
   * filename. Separators / and \\ are used.
   *
   * @param filename the name of the file to remove any path information from
   *
   * @return the file name without path information
   */
  public static String removePath(String filename) {
    if (filename != null) {
      filename = filename.replaceAll("^.*[/\\\\]", "");
    }

    return filename;
  }

  public static String asString(InputStream instream) {
    try (java.util.Scanner s = new java.util.Scanner(instream, "UTF-8").
        useDelimiter("\\A")) {
      return s.hasNext() ? s.next() : "";
    }
  }

   /**
    * Attempts to close the given stream, ignoring IO exceptions.
    *
    * @param closeable the stream to be closed
    */
  public static void tryClose(Closeable closeable) {
     try {
       closeable.close();
     }
     catch (IOException ex) {
       // Ignore
     }
   }

 
}
