package jetbrains.buildServer.artifacts.s3.publish.logger;

import java.time.Instant;
import jetbrains.buildServer.agent.FlowLogger;
import org.jetbrains.annotations.NotNull;

public class BuildLoggerS3Logger implements S3UploadLogger {
  private final FlowLogger myBuildProgressLogger;
  private final StatisticsLogger myStatisticsLogger;

  public BuildLoggerS3Logger(FlowLogger progressLogger, @NotNull final StatisticsLogger statisticsLogger) {
    myBuildProgressLogger = progressLogger;
    myStatisticsLogger = statisticsLogger;
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

  @Override
  public void uploadStarted(@NotNull String uploadKey) {
    myStatisticsLogger.uploadStarted(uploadKey, Instant.now());
  }

  @Override
  public void uploadFinished(@NotNull String uploadKey, @NotNull String uploadUrl) {
    myStatisticsLogger.uploadFinished(uploadKey, Instant.now());
  }

  @Override
  public void uploadFailed(@NotNull String uploadKey, @NotNull String error) {
    myStatisticsLogger.uploadFailed(uploadKey, error, Instant.now());
  }

  @Override
  public void partUploadStarted(@NotNull String uploadKey, int partNumber) {
    myStatisticsLogger.uploadStarted(uploadKey, Instant.now());
  }

  @Override
  public void partUploadFailed(@NotNull String uploadKey, @NotNull String error) {
    myStatisticsLogger.uploadFailed(uploadKey, error, Instant.now());
  }

  @Override
  public void partUploadFinished(@NotNull String uploadKey, @NotNull String uploadUrl, int finishedPercentage) {
    myStatisticsLogger.uploadFinished(uploadKey, Instant.now());
  }
}
