package jetbrains.buildServer.artifacts.s3.publish.presigned.upload;

import java.util.Collection;
import java.util.List;
import jetbrains.buildServer.artifacts.s3.transport.MultipartUploadAbortRequestDto;
import jetbrains.buildServer.artifacts.s3.transport.MultipartUploadCompleteRequestDto;
import jetbrains.buildServer.artifacts.s3.transport.MultipartUploadStartRequestDto;
import jetbrains.buildServer.artifacts.s3.transport.PresignedUrlDto;
import jetbrains.buildServer.util.amazon.retry.RecoverableException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PresignedUrlsProviderClient extends AutoCloseable {
  @NotNull
  PresignedUrlDto getUrl(@NotNull final String objectKey, @Nullable final String digest);

  @NotNull
  Collection<PresignedUrlDto> getRegularPresignedUrls(@NotNull List<String> objectKey);

  @NotNull
  PresignedUrlDto getMultipartPresignedUrl(@NotNull String objectKey, @NotNull List<String> digests);

  void completeMultipartUpload(@NotNull MultipartUploadCompleteRequestDto multipartUploadComplete);

  void abortMultipartUpload(@NotNull MultipartUploadAbortRequestDto multipartUploadAbort);

  void startMultipartUpload(@NotNull MultipartUploadStartRequestDto multipartUploadStart);

  @Override
  void close();

  static class MisconfigurationException extends RuntimeException {
    public MisconfigurationException(@NotNull final Throwable cause) {
      super(cause);
    }
  }

  class FetchFailedException extends RecoverableException {
    private final boolean myIsRecoverable;

    public FetchFailedException(@NotNull final Throwable cause) {
      super(cause.getMessage(), cause);
      myIsRecoverable = RecoverableException.isRecoverable(cause);
    }

    @Override
    public boolean isRecoverable() {
      return myIsRecoverable;
    }
  }
}
