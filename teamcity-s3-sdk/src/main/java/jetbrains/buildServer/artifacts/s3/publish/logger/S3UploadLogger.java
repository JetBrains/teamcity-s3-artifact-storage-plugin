package jetbrains.buildServer.artifacts.s3.publish.logger;

import org.jetbrains.annotations.NotNull;

public interface S3UploadLogger {
  void debug(@NotNull String message);

  void info(@NotNull String message);

  void warn(@NotNull String message);

  void error(@NotNull String message);

  void uploadStarted(@NotNull String uploadKey);

  void uploadFailed(@NotNull String uploadKey, @NotNull String error);

  void uploadFinished(@NotNull String uploadKey, @NotNull String uploadUrl);

  void partUploadStarted(@NotNull String uploadKey, int partNumber);

  void partUploadFailed(@NotNull String uploadKey, @NotNull String error);

  void partUploadFinished(@NotNull String uploadKey, @NotNull String uploadUrl, int finishedPercentage);
}
