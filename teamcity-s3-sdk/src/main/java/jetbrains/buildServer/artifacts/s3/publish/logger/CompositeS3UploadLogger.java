package jetbrains.buildServer.artifacts.s3.publish.logger;

import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class CompositeS3UploadLogger implements S3UploadLogger {
  @NotNull
  private final List<S3UploadLogger> myLoggers;

  private CompositeS3UploadLogger(@NotNull final List<S3UploadLogger> loggers) {
    myLoggers = loggers;
  }

  @NotNull
  public static S3UploadLogger compose(@NotNull final S3UploadLogger... logger) {
    return new CompositeS3UploadLogger(Arrays.asList(logger));
  }

  @Override
  public void debug(@NotNull String message) {
    for (S3UploadLogger logger : myLoggers) {
      logger.debug(message);
    }
  }

  @Override
  public void info(@NotNull String message) {
    for (S3UploadLogger logger : myLoggers) {
      logger.info(message);
    }
  }

  @Override
  public void warn(@NotNull String message) {
    for (S3UploadLogger logger : myLoggers) {
      logger.warn(message);
    }
  }

  @Override
  public void error(@NotNull String message) {
    for (S3UploadLogger logger : myLoggers) {
      logger.error(message);
    }
  }
}
