package jetbrains.buildServer.artifacts.s3.publish.logger;

import jetbrains.buildServer.agent.BuildProgressLogger;
import org.jetbrains.annotations.NotNull;

public class BuildLoggerS3Logger implements S3UploadLogger {
  private final BuildProgressLogger myBuildProgressLogger;

  public BuildLoggerS3Logger(@NotNull final BuildProgressLogger progressLogger) {
    this.myBuildProgressLogger = progressLogger;
  }

  @Override
  public void debug(@NotNull String message) {
    myBuildProgressLogger.debug(message);
  }

  @Override
  public void warn(@NotNull String message) {
    myBuildProgressLogger.warning(message);
  }

  @Override
  public void info(@NotNull String message) {
    myBuildProgressLogger.message(message);
  }

  @Override
  public void error(@NotNull String message) {
    myBuildProgressLogger.error(message);
  }
}
