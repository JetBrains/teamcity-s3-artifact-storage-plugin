package jetbrains.buildServer.artifacts.s3.download.parallel;

public class PartDownloadFailedException extends RuntimeException {
  public PartDownloadFailedException(Throwable cause) {
    super(cause);
  }
}
