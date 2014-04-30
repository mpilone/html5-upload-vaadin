
package org.mpilone.vaadin;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * A stream that counts the number of bytes writing and can generate an MD5
 * hash of the data written.
 */
class CountingDigestOutputStream extends OutputStream {
  private int count = 0;
  private MessageDigest md;
  private boolean closed = false;

  public CountingDigestOutputStream() {
    reset();
  }

  @Override
  public void close() throws IOException {
    super.close();
    closed = true;
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    if (closed) {
      throw new IOException("Output stream closed.");
    }
    count += len;
    md.update(b, off, len);
  }

  @Override
  public void write(byte[] b) throws IOException {
    if (closed) {
      throw new IOException("Output stream closed.");
    }

    count += b.length;
    md.update(b);
  }

  @Override
  public void write(int b) throws IOException {
    if (closed) {
      throw new IOException("Output stream closed.");
    }
    count++;
    md.update((byte) b);
  }

  public void reset() {
    count = 0;
    try {
      md = MessageDigest.getInstance("MD5");
    }
    catch (NoSuchAlgorithmException ex) {
      throw new RuntimeException("Unable to create MD5 digest.", ex);
    }
  }

  public int getCount() {
    return count;
  }

  public String getHash() {
    byte[] hash = md.digest();
    BigInteger bigInt = new BigInteger(1, hash);
    String hashText = bigInt.toString(16);
    while (hashText.length() < 32) {
      hashText = "0" + hashText;
    }
    return hashText;
  }

}
