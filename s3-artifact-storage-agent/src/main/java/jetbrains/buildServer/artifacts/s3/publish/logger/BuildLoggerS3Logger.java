package jetbrains.buildServer.artifacts.s3.publish.logger;

import jetbrains.buildServer.agent.FlowLogger;
import org.jetbrains.annotations.NotNull;

public class BuildLoggerS3Logger implements S3UploadLogger {
  private final FlowLogger myBuildProgressLogger;

  public BuildLoggerS3Logger(FlowLogger progressLogger) {
    myBuildProgressLogger = progressLogger;
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
