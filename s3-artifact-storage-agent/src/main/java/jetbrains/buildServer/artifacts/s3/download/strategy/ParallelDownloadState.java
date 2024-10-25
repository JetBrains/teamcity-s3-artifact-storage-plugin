package jetbrains.buildServer.artifacts.s3.download.strategy;


import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import jetbrains.buildServer.artifacts.FileProgress;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ParallelDownloadState {
  private final FileProgress externalDownloadProgress;
  private final AtomicBoolean isInterrupted;
  private final AtomicReference<Exception> firstFailedPartException;

  ParallelDownloadState(@NotNull AtomicBoolean isInterrupted, @NotNull FileProgress externalDownloadProgress) {
    this.externalDownloadProgress = externalDownloadProgress;
    this.isInterrupted = isInterrupted;
    firstFailedPartException = new AtomicReference<>(null);
  }

  boolean isInterrupted() {
    return isInterrupted.get();
  }

  void failPart(int partNumber, @NotNull RuntimeException exception) {
    failPart(partNumber, (Exception)exception);
  }

  void failPart(int partNumber, @NotNull IOException exception) {
    failPart(partNumber, (Exception)exception);
  }

  private void failPart(int partNumber, @NotNull Exception exception) {
    firstFailedPartException.compareAndSet(null, exception);
  }

  boolean hasFailedParts() {
    return firstFailedPartException.get() != null;
  }

  @Nullable
  Exception getFirstFailedPartException() {
    return firstFailedPartException.get();
  }

  void throwFirstFailedPartExceptionIfFailed() throws IOException {
    Exception exception = getFirstFailedPartException();
    if (exception != null) {
      if (exception instanceof IOException) throw (IOException)exception;
      if (exception instanceof RuntimeException) throw (RuntimeException)exception;
      // no other options
    }
  }

  void expectDownloadedBytes(long bytes) {
    externalDownloadProgress.setExpectedLength(bytes);
  }

  void addDownloadedBytes(int bytes) {
    externalDownloadProgress.transferred(bytes);
  }
}
