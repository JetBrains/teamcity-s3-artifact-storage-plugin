package jetbrains.buildServer.artifacts.s3.exceptions;

import jetbrains.buildServer.util.amazon.retry.RecoverableException;
import org.jetbrains.annotations.NotNull;

public class FileUploadFailedException extends RecoverableException {
  private final boolean myRecoverable;

  public FileUploadFailedException(@NotNull final String msg, @NotNull final RecoverableException e) {
    super(msg);
    this.myRecoverable = e.isRecoverable();
  }

  public FileUploadFailedException(@NotNull final String msg, final boolean recoverable) {
    super(msg);
    this.myRecoverable = recoverable;
  }

  public FileUploadFailedException(@NotNull final String msg, final boolean recoverable, @NotNull final Throwable cause) {
    super(msg, cause);
    if (cause instanceof RecoverableException) {
      this.myRecoverable = ((RecoverableException)cause).isRecoverable();
    } else {
      this.myRecoverable = recoverable;
    }
  }

  @Override
  public boolean isRecoverable() {
    return myRecoverable;
  }
}
