package jetbrains.buildServer.artifacts.s3.publish.presigned.upload;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.artifacts.s3.transport.MultipartUploadAbortRequestDto;
import jetbrains.buildServer.artifacts.s3.transport.MultipartUploadCompleteRequestDto;
import jetbrains.buildServer.artifacts.s3.transport.MultipartUploadStartRequestDto;
import jetbrains.buildServer.artifacts.s3.transport.PresignedUrlDto;
import jetbrains.buildServer.util.retry.RecoverableException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PresignedUrlsProviderClient {
  @NotNull
  PresignedUrlDto getUrl(@NotNull final String objectKey, @Nullable final String digest, @Nullable Long ttl);

  @NotNull
  Collection<PresignedUrlDto> getRegularPresignedUrls(@NotNull List<String> objectKey, Map<String, String> precalculatedDigests);

  @NotNull
  PresignedUrlDto getMultipartPresignedUrl(@NotNull String objectKey,
                                           @NotNull String contentType,
                                           @NotNull List<String> digests,
                                           @Nullable String uploadId,
                                           @Nullable Long ttl);

  void completeMultipartUpload(@NotNull MultipartUploadCompleteRequestDto multipartUploadComplete);

  void abortMultipartUpload(@NotNull MultipartUploadAbortRequestDto multipartUploadAbort);

  void startMultipartUpload(@NotNull MultipartUploadStartRequestDto multipartUploadStart);

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

    public FetchFailedException(@NotNull final String message){
      super(message);
      myIsRecoverable = false;
    }

    @Override
    public boolean isRecoverable() {
      return myIsRecoverable;
    }
  }
}
