package jetbrains.buildServer.artifacts.s3.download.parallel;


import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import jetbrains.buildServer.artifacts.FileProgress;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ParallelDownloadState {
  @NotNull
  private final FileProgress downloadProgress;
  @NotNull
  private final AtomicBoolean interruptedFlag;
  @NotNull
  private final AtomicReference<PartFailure> firstPartFailure;

  public ParallelDownloadState(@NotNull FileProgress downloadProgress, @NotNull AtomicBoolean interruptedFlag) {
    this.downloadProgress = downloadProgress;
    this.interruptedFlag = interruptedFlag;
    firstPartFailure = new AtomicReference<>(null);
  }

  public void partFailed(int partNumber, @NotNull Exception exception) {
    firstPartFailure.compareAndSet(null, new PartFailure(partNumber, new PartDownloadFailedException(exception)));
  }

  public boolean hasFailedParts() {
    return firstPartFailure.get() != null;
  }

  @Nullable
  public Integer getFirstFailedPartNumber() {
    PartFailure partFailure = firstPartFailure.get();
    return partFailure != null ? partFailure.partNumber : null;
  }

  @Nullable
  public PartDownloadFailedException getFirstFailedPartException() {
    PartFailure partFailure = firstPartFailure.get();
    return partFailure != null ? partFailure.exception : null;
  }

  public void expectDownloadedBytes(long bytes) {
    downloadProgress.setExpectedLength(bytes);
  }

  public void addDownloadedBytes(long bytes) {
    downloadProgress.transferred(bytes);
  }

  public boolean isInterrupted() {
    return interruptedFlag.get();
  }

  private class PartFailure {
    private final int partNumber;
    @NotNull
    private final PartDownloadFailedException exception;

    private PartFailure(int partNumber, @NotNull PartDownloadFailedException exception) {
      this.partNumber = partNumber;
      this.exception = exception;
    }
  }
}
