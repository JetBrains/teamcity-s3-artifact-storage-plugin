package jetbrains.buildServer.artifacts.s3.publish.presigned;

import org.jetbrains.annotations.NotNull;

public class PublishingInterruptedException extends RuntimeException {
  public PublishingInterruptedException(@NotNull final String reason) {
    super(reason);
  }
}
