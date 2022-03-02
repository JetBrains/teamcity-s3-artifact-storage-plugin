package jetbrains.buildServer.artifacts.s3.publish.logger;


import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

public class S3Log4jUploadLogger implements S3UploadLogger {
  @NotNull
  private static final Logger LOGGER = Logger.getInstance(S3Log4jUploadLogger.class);

  @Override
  public void debug(@NotNull String message) {
    LOGGER.debug(message);
  }

  @Override
  public void info(@NotNull String message) {
    LOGGER.info(message);
  }

  @Override
  public void warn(@NotNull String message) {
    LOGGER.warn(message);
  }

  @Override
  public void error(@NotNull String message) {
    LOGGER.error(message);
  }
}
