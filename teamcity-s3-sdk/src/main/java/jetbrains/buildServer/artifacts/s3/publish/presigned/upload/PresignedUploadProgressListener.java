package jetbrains.buildServer.artifacts.s3.publish.presigned.upload;

import java.time.Duration;
import org.jetbrains.annotations.NotNull;

public interface PresignedUploadProgressListener {
  void onPartUploadFailed(@NotNull final Throwable e, int partIndex);

  void onPartUploadSuccess(@NotNull String uploadUrl, int partIndex);

  void onFileUploadFailed(@NotNull String message, boolean isRecoverable);

  void onFileUploadSuccess(@NotNull String uploadUrl);

  void beforeUploadStarted();

  void beforePartUploadStarted(int partIndex);

  void setUpload(@NotNull S3PresignedUpload upload);

  void partsSeparated(@NotNull Duration ofMillis);

  void urlsGenerated(@NotNull Duration ofMillis);
}
