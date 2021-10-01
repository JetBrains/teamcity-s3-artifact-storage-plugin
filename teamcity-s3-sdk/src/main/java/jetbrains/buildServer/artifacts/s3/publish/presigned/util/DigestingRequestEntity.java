package jetbrains.buildServer.artifacts.s3.publish.presigned.util;

import org.jetbrains.annotations.NotNull;

public interface DigestingRequestEntity {
  @NotNull
  String getDigest();
}
