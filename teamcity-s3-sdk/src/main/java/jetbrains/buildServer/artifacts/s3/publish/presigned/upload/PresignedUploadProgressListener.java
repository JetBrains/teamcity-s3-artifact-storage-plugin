package jetbrains.buildServer.artifacts.s3.publish.presigned.upload;

import org.jetbrains.annotations.NotNull;

public interface PresignedUploadProgressListener {
  void onPartUploadFailed(@NotNull final Exception e);

  void onPartUploadSuccess(@NotNull String uploadUrl);

  void onFileUploadFailed(@NotNull final Exception e, boolean isRecoverable);

  void onFileUploadSuccess(@NotNull String uploadUrl);

  void beforeUploadStarted();

  void beforePartUploadStarted(int partNumber);

  void setUpload(@NotNull S3PresignedUpload upload);
}
