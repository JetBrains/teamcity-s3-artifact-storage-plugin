package jetbrains.buildServer.artifacts.s3.download.parallel;


import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import jetbrains.buildServer.artifacts.FileProgress;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ParallelDownloadState {
  @NotNull
  private final FileProgress myDownloadProgress;
  @NotNull
  private final AtomicBoolean myInterruptedFlag;
  @NotNull
  private final AtomicReference<PartFailure> myFirstPartFailure;

  public ParallelDownloadState(@NotNull FileProgress downloadProgress, @NotNull AtomicBoolean interruptedFlag) {
    myDownloadProgress = downloadProgress;
    myInterruptedFlag = interruptedFlag;
    myFirstPartFailure = new AtomicReference<>(null);
  }

  public void partFailed(@NotNull FilePart filePart, @NotNull IOException exception) {
    myFirstPartFailure.compareAndSet(null, new PartFailure(filePart, exception));
  }

  public boolean hasFailedParts() {
    return myFirstPartFailure.get() != null;
  }

  @Nullable
  public PartFailure getFirstPartFailure() {
    return myFirstPartFailure.get();
  }

  public void expectDownloadedBytes(long bytes) {
    myDownloadProgress.setExpectedLength(bytes);
  }

  public void addDownloadedBytes(long bytes) {
    myDownloadProgress.transferred(bytes);
  }

  public boolean isInterrupted() {
    return myInterruptedFlag.get();
  }
}
