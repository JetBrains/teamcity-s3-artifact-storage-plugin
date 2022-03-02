package jetbrains.buildServer.artifacts.s3.publish.logger;

import org.jetbrains.annotations.NotNull;

public interface S3UploadLogger {
  void debug(@NotNull String message);

  void info(@NotNull String message);

  void warn(@NotNull String message);

  void error(@NotNull String message);
}
