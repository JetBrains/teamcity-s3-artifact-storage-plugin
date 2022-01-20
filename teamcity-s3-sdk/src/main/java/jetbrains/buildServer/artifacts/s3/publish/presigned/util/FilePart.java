package jetbrains.buildServer.artifacts.s3.publish.presigned.util;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FilePart {
  private static final int OUR_CHUNK_SIZE = TeamCityProperties.getInteger(S3Constants.S3_PRESIGNED_UPLOAD_INTERNAL_CHUNK_SIZE, 64 * 1024);

  private final File myFile;
  private final long myStart;
  private final long myLength;
  private final int partNumber;
  private String digest;

  public FilePart(@NotNull File file, long start, long length, int partNumber) {
    myFile = file;
    myStart = start;
    myLength = length;
    this.partNumber = partNumber;
  }

  public long getStart() {
    return myStart;
  }

  public long getLength() {
    return myLength;
  }

  public int getPartNumber() {
    return partNumber;
  }

  public File getFile() {
    return myFile;
  }

  @Nullable
  public String getDigest() {
    return digest;
  }

  public void write(OutputStream out) throws IOException {
    stream((buf, start, length) -> out.write(buf, start, length));
  }

  public void calculateDigest() throws IOException {
    MessageDigest digestCalculator;
    try {
      digestCalculator = MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      throw new IOException("Could not find MD5 algorithm", e);
    }
    stream((buf, start, length) -> digestCalculator.update(buf, start, length));
    digest = Base64.getEncoder().encodeToString(digestCalculator.digest());
  }

  private void stream(WriteFunction function) throws IOException {
    long remaining = myLength;
    final byte[] buffer = new byte[(int)Math.min(OUR_CHUNK_SIZE, remaining)];

    try (final FileInputStream fis = new FileInputStream(myFile);
         final BufferedInputStream bis = new BufferedInputStream(fis)) {

      final long skip = bis.skip(myStart);
      if (skip != myStart) {
        throw new IOException("Reader has skipped " + skip + " bytes when supposed to skip " + myStart);
      }
      do {
        final int currentChunkSize = (int)Math.min(buffer.length, remaining);
        final int readContentLength = bis.read(buffer, 0, currentChunkSize);
        if (readContentLength == -1) {
          //eof
          break;
        }
        remaining -= readContentLength;
        function.apply(buffer, 0, readContentLength);
      } while (remaining > 0);
    }
  }

  @FunctionalInterface
  interface WriteFunction {
    void apply(byte[] buffer, int start, int length) throws IOException;
  }
}
