package jetbrains.buildServer.artifacts.s3.download.strategy;

import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import jetbrains.buildServer.artifacts.FileProgress;
import jetbrains.buildServer.artifacts.RecoverableIOException;
import jetbrains.buildServer.artifacts.s3.download.S3HttpClient;
import jetbrains.buildServer.artifacts.s3.download.S3DownloadConfiguration;
import jetbrains.buildServer.artifacts.s3.download.S3DownloadHttpUtil;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.artifacts.s3.download.S3DownloadHttpUtil.checkResponseStatus;

/**
 * Abstract downloader that download a file in parts if it is large enough.
 */
public abstract class ParallelDownloadStrategy implements FileDownloadStrategy {
  @NotNull
  private final S3HttpClient myHttpClient;
  @NotNull
  private final ExecutorService myPartDownloadTaskExecutor;
  @NotNull
  private final AtomicBoolean myIsInterrupted;
  @NotNull
  private final FileProgress myExternalDownloadProgress;

  @NotNull
  protected final Logger LOGGER = Logger.getInstance(getClass());
  @NotNull
  protected final S3DownloadConfiguration myDownloadConfiguration;

  public ParallelDownloadStrategy(@NotNull S3HttpClient httpClient,
                                  @NotNull ExecutorService partDownloadTaskExecutor,
                                  @NotNull S3DownloadConfiguration downloadConfiguration,
                                  @NotNull AtomicBoolean isInterrupted,
                                  @NotNull FileProgress externalDownloadProgress) {
    myHttpClient = httpClient;
    myPartDownloadTaskExecutor = partDownloadTaskExecutor;
    myDownloadConfiguration = downloadConfiguration;
    myIsInterrupted = isInterrupted;
    myExternalDownloadProgress = externalDownloadProgress;
  }

  @Override
  public void download(String srcUrl, Path targetFile, long fileSizeBytes) throws IOException {
    List<FilePart> fileParts;
    try {
      fileParts = splitIntoParts(targetFile, fileSizeBytes); // should be under try-catch because might be accessing filesystem
      LOGGER.debug(String.format(
        "Start downloading file %s from %s in %s parts of %s MB each (except the last) by max %s threads", // MB because S3DownloadConfiguration.getMinPartSizeBytes() is 5 MB
        targetFile, srcUrl, fileParts.size(), myDownloadConfiguration.getPartSizeBytes() / (1024 * 1024), Math.min(myDownloadConfiguration.getMaxThreads(), fileParts.size())
      ));
    } catch (RuntimeException e) {
      throw new RuntimeException(String.format("Failed to split file %s into parts", targetFile), e);
    }

    try {
      ParallelDownloadState downloadState = new ParallelDownloadState(myIsInterrupted, myExternalDownloadProgress);

      // prepare before downloading parts
      checkIfInterrupted(downloadState);
      beforeDownloadingParts(targetFile, fileSizeBytes, fileParts, downloadState);
      LOGGER.debug(String.format("Finished preparations before downloading parts of file %s from %s", targetFile, srcUrl));

      // start downloading parts
      downloadState.expectDownloadedBytes(fileSizeBytes);
      List<CompletableFuture<Void>> partDownloadFutures = new CopyOnWriteArrayList<>();
      checkIfInterrupted(downloadState);
      fileParts.stream()
        .map(filePart -> {
          if (downloadState.isInterrupted() || downloadState.hasFailedParts()) return null;
          return CompletableFuture.runAsync(() -> {
            try {
              downloadPart(srcUrl, filePart, downloadState);
            } catch (IOException | RuntimeException partDownloadException) {
              if (partDownloadException instanceof IOException) {
                downloadState.failPart(filePart.getPartNumber(), (IOException)partDownloadException);
              } else {
                downloadState.failPart(filePart.getPartNumber(), (RuntimeException)partDownloadException);
              }
              partDownloadFutures.forEach(future -> future.cancel(false)); // will cancel unstarted part downloads at the executor level
            }
          }, myPartDownloadTaskExecutor);
        })
        .filter(Objects::nonNull) // null when interrupted or detected failure
        .forEach(partDownloadFuture -> partDownloadFutures.add(partDownloadFuture));

      // wait until part downloads finish and check for uncaught errors and executor level exceptions
      CompletableFuture<Void> allPartsDownloadFuture = CompletableFuture.allOf(partDownloadFutures.toArray(new CompletableFuture[]{}));
      checkIfInterrupted(downloadState);
      try {
        allPartsDownloadFuture.join();
      } catch (CompletionException allPartsDownloadException) {
        // rethrow error from executor or downloadPart method
        Throwable somePartException = allPartsDownloadException.getCause();
        Throwable somePartExceptionCause = somePartException.getCause();
        if (somePartException instanceof Error) throw (Error)somePartException;
        if (somePartExceptionCause != null && somePartExceptionCause instanceof Error) throw (Error)somePartExceptionCause;

        // exception is from executor
        // it might be a cancellation exception if some part failed, throw this part's exception instead
        downloadState.throwFirstFailedPartExceptionIfFailed();

        // this happens when executor fails to run any of the part tasks (e.g. executor shut down)
        throw new IOException("Failed to start all part downloads", somePartException);
      }

      // check for caught part download exceptions
      downloadState.throwFirstFailedPartExceptionIfFailed();
      LOGGER.debug(String.format("Finished downloading parts of file %s from %s", targetFile, srcUrl));

      // do something with downloaded parts
      checkIfInterrupted(downloadState);
      afterDownloadingParts(targetFile, fileSizeBytes, fileParts, downloadState);
      LOGGER.debug(String.format("Finished processing after downloading parts of file %s from %s", targetFile, srcUrl));
    } catch (IOException | RuntimeException e) {
      cleanupUnfinishedDownload(targetFile, fileSizeBytes, fileParts);
      throw e;
    }
  }

  @NotNull
  protected List<FilePart> splitIntoParts(@NotNull Path targetFile, long fileSizeBytes) {
    long partSizeBytes = myDownloadConfiguration.getPartSizeBytes();
    if (fileSizeBytes < partSizeBytes) {
      return Collections.singletonList(createPart(0, 0, fileSizeBytes - 1, targetFile));
    }

    long residualSize = fileSizeBytes % partSizeBytes;
    long fullPartsLong = (fileSizeBytes / partSizeBytes);
    if (fullPartsLong > Integer.MAX_VALUE) {
      throw new RuntimeException(String.format("Too many (%s) parts: file size - %s bytes, configured part size - %s bytes", fullPartsLong, fileSizeBytes, partSizeBytes));
    }

    int fullParts = (int)fullPartsLong;
    boolean extendLastFullPart = residualSize < myDownloadConfiguration.getMinPartSizeBytes(); // don't create new part for too few bytes, extend the last full part

    List<FilePart> parts = new ArrayList<>();
    for (int partNumber = 0; partNumber < fullParts - 1; partNumber++) {
      long startByte = partSizeBytes * partNumber;
      long endByte = startByte + partSizeBytes - 1;
      parts.add(createPart(partNumber, startByte, endByte, targetFile));
    }

    int lastFullPartNumber = fullParts - 1;
    long lastFullPartStartByte = partSizeBytes * lastFullPartNumber;
    long lastFullPartEndByte = extendLastFullPart ? fileSizeBytes - 1 : lastFullPartStartByte + partSizeBytes - 1;
    parts.add(createPart(lastFullPartNumber, lastFullPartStartByte, lastFullPartEndByte, targetFile));

    if (!extendLastFullPart && residualSize > 0) {
      int lastPartNumber = lastFullPartNumber + 1;
      long lastPartStartByte = partSizeBytes * lastPartNumber;
      long lastPartEndByte = fileSizeBytes - 1;
      parts.add(createPart(lastPartNumber, lastPartStartByte, lastPartEndByte, targetFile));
    }

    return parts;
  }

  @NotNull
  protected abstract FilePart createPart(int partNumber, long startByte, long endByte, @NotNull Path targetFile);

  protected final void checkIfInterrupted(@NotNull ParallelDownloadState downloadState) throws RecoverableIOException {
    if (downloadState.isInterrupted()) throw new RecoverableIOException("Download was interrupted");
  }

  protected abstract void beforeDownloadingParts(@NotNull Path targetFile, long fileSizeBytes, @NotNull List<FilePart> fileParts, @NotNull ParallelDownloadState downloadState)
    throws IOException;

  private void downloadPart(String srcUrl, FilePart filePart, ParallelDownloadState downloadState) throws IOException {
    long startByte = filePart.getStartByte();
    long endByte = filePart.getEndByte();
    Path partTarget = filePart.getTargetFile();

    LOGGER.debug(String.format("Start downloading part %s-%s to file %s", startByte, endByte, partTarget));
    GetMethod request = null;
    try {
      request = new GetMethod(srcUrl);
      request.addRequestHeader(S3DownloadHttpUtil.createRangeHeader(startByte, endByte));

      checkIfInterruptedOrOtherPartFailed(downloadState);
      int statusCode = myHttpClient.execute(request);

      checkResponseStatus(statusCode, HttpStatus.SC_PARTIAL_CONTENT);
      checkIfInterruptedOrOtherPartFailed(downloadState);
      writePart(request, filePart, downloadState);
      LOGGER.debug(String.format("Part %s-%s downloaded to file %s", startByte, endByte, partTarget));
    } catch (IOException | RuntimeException e) {
      LOGGER.debug(String.format("Failed to download part %s-%s to file %s", startByte, endByte, partTarget), e); // debug because there is a general log for the whole file failure
      if (request != null) request.abort(); // aborting the request allows not to wait until full body arrives
      throw new IOException(String.format("Failed to download part %s-%s", startByte, endByte), e);
    } finally {
      if (request != null) request.releaseConnection();
    }
  }

  protected final void checkIfInterruptedOrOtherPartFailed(@NotNull ParallelDownloadState downloadState) throws RecoverableIOException {
    checkIfInterrupted(downloadState);
    if (downloadState.hasFailedParts()) throw new RecoverableIOException("Detected other part download failure", downloadState.getFirstFailedPartException());
  }

  protected abstract void writePart(@NotNull HttpMethod ongoingRequest, @NotNull FilePart filePart, @NotNull ParallelDownloadState downloadState) throws IOException;

  protected abstract void afterDownloadingParts(@NotNull Path targetFile, long fileSizeBytes, @NotNull List<FilePart> fileParts, @NotNull ParallelDownloadState downloadState)
    throws IOException;

  protected abstract void cleanupUnfinishedDownload(@NotNull Path targetFile, long fileSizeBytes, @NotNull List<FilePart> fileParts);
}
