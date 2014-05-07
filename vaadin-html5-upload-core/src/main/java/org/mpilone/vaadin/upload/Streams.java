
package org.mpilone.vaadin.upload;

import java.io.*;
import java.nio.ByteBuffer;


/**
 * Utility methods for working with streams.
 *
 * @author mpilone
 */
public class Streams {
  /**
   * The byte size of the buffer to use when reading from an input stream and
   * writing to an output stream (i.e. stream data copying).
   */
  static final int IO_BUFFER_SIZE = 4 * 1024;

  /**
   * Copies all the data from the given input stream to the output stream.
   *
   * @param instream the input stream to read from
   * @param outstream the output stream to write to
   *
   * @throws IOException if an error occurs reading or writing
   */
  public static void copy(InputStream instream, OutputStream outstream) throws
      IOException {
    byte[] buf = new byte[IO_BUFFER_SIZE];

    int read;
    while ((read = instream.read(buf)) != -1) {
      outstream.write(buf, 0, read);
    }
  }

  /**
   * Copies {@code length} bytes from the buffer to the output stream. The
   * buffer must be in a position to read.
   *
   * @param buffer the buffer to read from
   * @param length the number of bytes to read from the buffer
   * @param outstream the output stream to write to
   *
   * @throws IOException if an error occurs reading or writing
   */
  static void copy(ByteBuffer buffer, int length, OutputStream outstream)
      throws IOException {

    byte[] buf = new byte[IO_BUFFER_SIZE];
    while (length > 0) {
      int len = Math.min(buf.length, length);
      length -= len;

      buffer.get(buf, 0, len);
      outstream.write(buf, 0, len);
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

  /**
   * Reads the entire input stream into a String using UTF-8 encoding and
   * returns the String.
   *
   * @param instream the input stream to read
   *
   * @return the contents of the stream as a String
   */
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
