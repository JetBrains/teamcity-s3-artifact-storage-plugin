package jetbrains.buildServer.artifacts.s3.publish.presigned.upload;

import org.jetbrains.annotations.NotNull;

public interface PresignedUploadProgressListener {
  void onPartUploadFailed(@NotNull final Exception e);

  void onPartUploadSuccess();

  void onFileUploadFailed(@NotNull final Exception e);

  void onFileUploadSuccess();

  void beforeUploadStarted();

  void beforePartUploadStarted();

  void setUpload(@NotNull S3PresignedUpload upload);
}
