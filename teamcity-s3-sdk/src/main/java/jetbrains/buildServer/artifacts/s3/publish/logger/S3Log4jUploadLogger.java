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

  @Override
  public void uploadStarted(@NotNull String uploadKey) {
    LOGGER.debug("Started uploading " + uploadKey);
  }

  @Override
  public void uploadFinished(@NotNull String uploadKey, @NotNull String uploadUrl) {
    LOGGER.debug("Artifact upload " + uploadKey + " to " + uploadUrl + " is finished");
  }

  @Override
  public void uploadFailed(@NotNull String uploadKey, @NotNull String error) {
    LOGGER.warn("Upload " + uploadKey + " failed with error: " + error);
  }

  @Override
  public void partUploadStarted(@NotNull String uploadKey, int partNumber) {
    LOGGER.debug(String.format("Started uploading part #%d of %s", partNumber, uploadKey));
  }

  @Override
  public void partUploadFailed(@NotNull String uploadKey, @NotNull String error) {
    LOGGER.warn("Upload chunk " + uploadKey + " failed with error: " + error);
  }

  @Override
  public void partUploadFinished(@NotNull String uploadKey, @NotNull String uploadUrl, int finishedPercentage) {
    LOGGER.debug("Artifact upload " + uploadKey + " to " + uploadUrl + " at " + finishedPercentage + "%");
  }
}
