package jetbrains.buildServer.artifacts.s3.download.parallel;


import java.io.IOException;
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

  public void partFailed(@NotNull FilePart filePart, @NotNull IOException exception) {
    firstPartFailure.compareAndSet(null, new PartFailure(filePart, exception));
  }

  public boolean hasFailedParts() {
    return firstPartFailure.get() != null;
  }

  @Nullable
  public PartFailure getFirstPartFailure() {
    return firstPartFailure.get();
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
}
