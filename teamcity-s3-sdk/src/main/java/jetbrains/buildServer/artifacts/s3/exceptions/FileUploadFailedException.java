package jetbrains.buildServer.artifacts.s3.exceptions;

import org.jetbrains.annotations.NotNull;

public class FileUploadFailedException extends RuntimeException {
  private final boolean myRetryable;

  public FileUploadFailedException(@NotNull final String msg, final boolean retryable) {
    super(msg);
    this.myRetryable = retryable;
  }

  public FileUploadFailedException(@NotNull final String msg, final boolean retryable, @NotNull final Throwable cause) {
    super(msg, cause);
    this.myRetryable = retryable;
  }

  public boolean isRetryable() {
    return myRetryable;
  }
}
