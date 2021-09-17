package jetbrains.buildServer.artifacts.s3.publish.presigned.upload;

import java.util.Collection;
import java.util.List;
import jetbrains.buildServer.artifacts.s3.transport.PresignedUrlDto;
import org.jetbrains.annotations.NotNull;

public interface PresignedUrlsProviderClient extends AutoCloseable {

  @NotNull
  Collection<PresignedUrlDto> getRegularPresignedUrls(@NotNull List<String> objectKey);

  @NotNull
  PresignedUrlDto getMultipartPresignedUrl(@NotNull String objectKey, int nParts);

  void completeMultipartUpload(@NotNull S3PresignedUpload upload, @NotNull String uploadId);

  void abortMultipartUpload(@NotNull S3PresignedUpload upload, @NotNull String uploadId);

  void startMultipartUpload(@NotNull String objectKey, @NotNull String uploadId);

  static class MisconfigurationException extends RuntimeException {
    public MisconfigurationException(@NotNull final Throwable cause) {
      super(cause);
    }
  }

  class FetchFailedException extends RuntimeException {
    public FetchFailedException(@NotNull final Throwable cause) {
      super(cause);
    }
  }

  @Override
  void close();
}
