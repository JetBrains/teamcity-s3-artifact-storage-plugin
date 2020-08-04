package jetbrains.buildServer.artifacts.s3.migration;

public class PublishingFailedException extends RuntimeException {

  public PublishingFailedException(String message, Throwable cause) {
    super(message, cause);
  }
}
