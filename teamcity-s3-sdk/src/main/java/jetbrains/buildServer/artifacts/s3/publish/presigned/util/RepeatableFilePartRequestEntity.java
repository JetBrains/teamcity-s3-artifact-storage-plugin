package jetbrains.buildServer.artifacts.s3.publish.presigned.util;

import java.io.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicReference;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.jetbrains.annotations.NotNull;

public class RepeatableFilePartRequestEntity implements RequestEntity, DigestingRequestEntity {
  private static final int OUR_CHUNK_SIZE = TeamCityProperties.getInteger(S3Constants.S3_PRESIGNED_UPLOAD_INTERNAL_CHUNK_SIZE, 64 * 1024);
  private final long myStart;
  private final long myLength;
  @NotNull
  private final File myFile;
  @NotNull
  private final AtomicReference<String> myDigest = new AtomicReference<>(null);

  public RepeatableFilePartRequestEntity(@NotNull final File file, final long start, final long length) {
    myFile = file;
    myStart = start;
    myLength = length;
  }

  @Override
  public boolean isRepeatable() {
    return true;
  }

  @Override
  public void writeRequest(@NotNull final OutputStream out) throws IOException {
    long remaining = myLength;
    final MessageDigest digest = DigestUtil.createDigest();
    final byte[] buffer = new byte[(int)Math.min(OUR_CHUNK_SIZE, remaining)];

    try (final FileInputStream fis = new FileInputStream(myFile);
         final BufferedInputStream bis = new BufferedInputStream(fis);
         final DigestInputStream dis = new DigestInputStream(bis, digest)) {

      final long skip = dis.skip(myStart);
      if (skip != myStart) {
        throw new IOException("Reader has skipped " + skip + " bytes when supposed to skip " + myStart);
      }
      do {
        final int currentChunkSize = (int)Math.min(buffer.length, remaining);
        final int readContentLength = dis.read(buffer, 0, currentChunkSize);
        if (readContentLength == -1) {
          //eof
          break;
        }
        remaining -= readContentLength;
        out.write(buffer, 0, readContentLength);
      } while (remaining > 0);
      myDigest.set(DigestUtil.asString(digest));
    }
  }

  @Override
  public long getContentLength() {
    return myLength;
  }

  @NotNull
  @Override
  public String getContentType() {
    return S3Util.getContentType(myFile);
  }

  @NotNull
  @Override
  public String getDigest() {
    final String digest = myDigest.get();
    if (digest == null) {
      throw new IllegalStateException("Digest is not calculated yet");
    }
    return digest;
  }
}
