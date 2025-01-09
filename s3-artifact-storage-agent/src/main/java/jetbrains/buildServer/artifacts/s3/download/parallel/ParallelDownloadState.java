package jetbrains.buildServer.artifacts.s3.download.parallel;


import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import jetbrains.buildServer.artifacts.FileProgress;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ParallelDownloadState {
  @NotNull
  private final FileProgress myDownloadProgress;
  @NotNull
  private final ConcurrentHashMap<Long, FilePart> myPartsByThread = new ConcurrentHashMap<>();
  @NotNull
  private final ConcurrentHashMap<Long, AtomicLong> myRecievedBytesByThread = new ConcurrentHashMap<>();
  @NotNull
  private final ConcurrentHashMap<Long, AtomicLong> myWrittenBytesByThread = new ConcurrentHashMap<>();
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

  public void startedDownloadinPartByThread(FilePart filePart) {
    myPartsByThread.putIfAbsent(Thread.currentThread().getId(), filePart);
  }

  public void addDownloadedBytes(long bytes) {
    myDownloadProgress.transferred(bytes);
  }

  public void addRecievedBytes(long bytes) {
    myRecievedBytesByThread.computeIfAbsent(Thread.currentThread().getId(),k -> new AtomicLong()).addAndGet(bytes);
  }

  public void addWrittenBytes(long bytes) {
    myWrittenBytesByThread.computeIfAbsent(Thread.currentThread().getId(), k -> new AtomicLong()).addAndGet(bytes);
  }

  public Map<Long, String> getThreadIdToProgressReport() {
    return myPartsByThread.entrySet()
                                   .stream()
                                   .collect(Collectors.toMap(e -> e.getKey(), e -> {
                                     Long threadId = e.getKey();
                                     long recieved = myRecievedBytesByThread.get(threadId).get();
                                     long written = myWrittenBytesByThread.get(threadId).get();
                                     FilePart threadPart = myPartsByThread.get(threadId);
                                     double writtenPercent = Math.round((double)written * 100 / threadPart.getSizeBytes());
                                     return String.format("recieved %s, written %s (%s%%) of %s (part %s)",
                                                          recieved, written, writtenPercent, threadPart.getSizeBytes(), threadPart.getDescription());
                                   }));
  }

  public boolean isInterrupted() {
    return myInterruptedFlag.get();
  }
}
