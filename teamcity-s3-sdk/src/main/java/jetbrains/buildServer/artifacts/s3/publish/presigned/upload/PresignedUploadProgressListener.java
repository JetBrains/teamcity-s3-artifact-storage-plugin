package jetbrains.buildServer.artifacts.s3.publish.presigned.upload;

import org.jetbrains.annotations.NotNull;

public interface PresignedUploadProgressListener {
  void onPartUploadFailed(@NotNull final Throwable e);

  void onPartUploadSuccess(@NotNull String uploadUrl);

  void onFileUploadFailed(@NotNull String message, boolean isRecoverable);

  void onFileUploadSuccess(@NotNull String uploadUrl);

  void beforeUploadStarted();

  void beforePartUploadStarted(int partNumber);

  void setUpload(@NotNull S3PresignedUpload upload);
}
