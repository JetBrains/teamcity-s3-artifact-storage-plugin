package jetbrains.buildServer.artifacts.s3.publish.presigned.util;

import com.amazonaws.util.IOUtils;
import java.io.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.jetbrains.annotations.NotNull;

public class DigestingFileRequestEntity implements RequestEntity, DigestingRequestEntity {
  @NotNull
  private final File myFile;
  @NotNull
  private final String myContentType;
  @NotNull
  private final AtomicReference<String> myDigest = new AtomicReference<>(null);

  public DigestingFileRequestEntity(@NotNull final File file, @NotNull String contentType) {
    myFile = file;
    myContentType = contentType;
  }

  @Override
  public boolean isRepeatable() {
    return true;
  }

  @Override
  public void writeRequest(final OutputStream out) throws IOException {
    final MessageDigest messageDigest = DigestUtil.createDigest();
    try (final FileInputStream fis = new FileInputStream(myFile);
         final DigestInputStream dis = new DigestInputStream(fis, messageDigest);
         final BufferedInputStream bis = new BufferedInputStream(dis)) {
      IOUtils.copy(bis, out);
    }
    myDigest.set(DigestUtil.asString(messageDigest));
  }

  @Override
  public long getContentLength() {
    return myFile.length();
  }

  @NotNull
  @Override
  public String getContentType() {
    return myContentType;
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
