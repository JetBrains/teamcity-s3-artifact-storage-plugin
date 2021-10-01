package jetbrains.buildServer.artifacts.s3.publish.presigned.util;

import com.intellij.openapi.diagnostic.Logger;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import jetbrains.buildServer.artifacts.s3.exceptions.FileUploadFailedException;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.jetbrains.annotations.NotNull;

public final class DigestUtil {
  @NotNull
  private static final Logger LOGGER = Logger.getInstance(DigestUtil.class);

  private DigestUtil() {
  }

  @NotNull
  public static MessageDigest createDigest() throws IOException {
    try {
      return MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      throw new IOException("Unable to digest the file parts", e);
    }
  }

  @NotNull
  public static String asString(@NotNull final MessageDigest digest) {
    return new String(Hex.encodeHex(digest.digest()));
  }

  public static String multipartDigest(@NotNull final List<String> etags) {
    try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      etags.forEach(etag -> {
        try {
          outputStream.write(Hex.decodeHex(etag));
        } catch (DecoderException | IOException e) {
          throw new FileUploadFailedException("Decode of etag " + etag + " failed", true);
        }
      });
      return DigestUtils.md5Hex(outputStream.toByteArray()) + "-" + etags.size();
    } catch (IOException e) {
      LOGGER.debug("Got exception while closing bytearrayoutputstream", e);
      return "";
    }
  }
}
