package jetbrains.buildServer.artifacts.s3.exceptions;

import jetbrains.buildServer.util.ExceptionUtil;
import jetbrains.buildServer.util.amazon.retry.RecoverableException;
import org.jetbrains.annotations.NotNull;

public class FileUploadFailedException extends RecoverableException {
  private final boolean myRecoverable;

  public FileUploadFailedException(@NotNull final String msg, @NotNull final RecoverableException e) {
    super(msg);
    myRecoverable = e.isRecoverable();
  }

  public FileUploadFailedException(@NotNull final String msg, final boolean recoverable) {
    super(msg);
    myRecoverable = recoverable;
  }

  public FileUploadFailedException(@NotNull final String msg, final boolean recoverable, @NotNull final Throwable cause) {
    super(msg, cause);
    RecoverableException recoverableException = ExceptionUtil.getCause(cause, RecoverableException.class);
    if (recoverableException != null) {
      myRecoverable = ((RecoverableException)cause).isRecoverable();
    } else {
      myRecoverable = recoverable;
    }
  }

  @Override
  public boolean isRecoverable() {
    return myRecoverable;
  }
}
