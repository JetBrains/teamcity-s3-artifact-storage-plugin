package jetbrains.buildServer.artifacts.s3.download.parallel.strategy.impl;

import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import jetbrains.buildServer.artifacts.FileProgress;
import jetbrains.buildServer.artifacts.RecoverableIOException;
import jetbrains.buildServer.artifacts.s3.download.S3DownloadConfiguration;
import jetbrains.buildServer.artifacts.s3.download.S3DownloadHttpUtil;
import jetbrains.buildServer.artifacts.s3.download.parallel.*;
import jetbrains.buildServer.artifacts.s3.download.parallel.strategy.ParallelDownloadStrategy;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.artifacts.s3.download.S3DownloadHttpUtil.checkResponseStatus;

/**
 * Abstract strategy that downloads a file in parts.
 */
public abstract class AbstractParallelDownloadStrategy implements ParallelDownloadStrategy {
  @NotNull
  protected final Logger LOGGER = Logger.getInstance(getClass());

  @Override
  public void download(@NotNull String srcUrl,
                       @NotNull Path targetFile,
                       long fileSize,
                       @NotNull FileProgress downloadProgress,
                       @NotNull ParallelDownloadContext downloadContext) throws IOException {
    if (fileSize <= 0) throw new IllegalArgumentException(String.format("File size is not positive (%s)", fileSize));
    S3DownloadConfiguration configuration = downloadContext.getConfiguration();

    List<FilePart> fileParts;
    try {
      fileParts = splitIntoParts(targetFile, fileSize, downloadContext); // should be under try-catch because might be accessing filesystem
      LOGGER.debug(String.format(
        "Start downloading file %s from %s in %s parts of min %s MB each (except, possibly, the last) by max %s threads", // MB in logs because not less than 1 MB
        targetFile, srcUrl, fileParts.size(), configuration.getMinPartSizeBytes() / (1024 * 1024), Math.min(configuration.getMaxThreads(), fileParts.size())
      ));
    } catch (RuntimeException e) {
      throw new RuntimeException(String.format("Failed to split file %s into parts", targetFile), e);
    }

    ParallelDownloadState downloadState = new ParallelDownloadState(downloadProgress, downloadContext.getInterruptedFlag());
    try {
      // prepare before downloading parts
      checkDownloadInterrupted(downloadState);
      beforeDownloadingParts(targetFile, fileSize, fileParts, downloadState, downloadContext);
      LOGGER.debug(String.format("Finished preparations before downloading parts of file %s from %s", targetFile, srcUrl));

      // start downloading parts
      downloadState.expectDownloadedBytes(fileSize);
      List<CompletableFuture<Void>> partDownloadFutures = new CopyOnWriteArrayList<>();
      checkDownloadInterrupted(downloadState);
      fileParts.stream()
        .map(filePart -> {
          if (downloadState.isInterrupted() || downloadState.hasFailedParts()) return null;
          return CompletableFuture.runAsync(() -> {
            try {
              downloadPart(srcUrl, filePart, fileSize, downloadState, downloadContext);
            } catch (IOException | RuntimeException partDownloadException) {
              downloadState.partFailed(filePart.getPartNumber(), partDownloadException);
              partDownloadFutures.forEach(future -> future.cancel(false)); // will cancel unstarted part downloads at the executor level
            }
          }, downloadContext.getExecutor());
        })
        .filter(Objects::nonNull) // null when interrupted or detected failure
        .forEach(partDownloadFuture -> partDownloadFutures.add(partDownloadFuture));

      // wait until part downloads finish and check for uncaught errors and executor level exceptions
      CompletableFuture<Void> allPartsDownloadFuture = CompletableFuture.allOf(partDownloadFutures.toArray(new CompletableFuture[]{}));
      checkDownloadInterrupted(downloadState);
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
        throwFirstFailedPartExceptionIfFailed(downloadState);

        // this happens when executor fails to run any of the part tasks (e.g. executor shut down)
        throw new IOException("Failed to start all part downloads", somePartException);
      }

      // check for caught part download exceptions
      throwFirstFailedPartExceptionIfFailed(downloadState);
      LOGGER.debug(String.format("Finished downloading parts of file %s from %s", targetFile, srcUrl));

      // do something with downloaded parts
      checkDownloadInterrupted(downloadState);
      afterDownloadingParts(targetFile, fileSize, fileParts, downloadState, downloadContext);
      LOGGER.debug(String.format("Finished processing after downloading parts of file %s from %s", targetFile, srcUrl));
    } catch (IOException | RuntimeException e) {
      cleanupUnfinishedDownload(targetFile, fileParts, downloadState, downloadContext);
      throw e;
    }
  }

  // todo test extensively
  @NotNull
  private List<FilePart> splitIntoParts(@NotNull Path targetFile, long fileSize, @NotNull ParallelDownloadContext downloadContext) {
    S3DownloadConfiguration configuration = downloadContext.getConfiguration();
    final long fileSizeThreshold = configuration.getParallelDownloadFileSizeThreshold();
    if (fileSize < fileSizeThreshold) {
      // only one part of size fileSize
      return Collections.singletonList(createPart(0, 0, fileSize - 1, targetFile, downloadContext));
    }

    List<FilePart> parts = new ArrayList<>();
    final long minPartSize = configuration.getMinPartSizeBytes();
    final int maxThreads = configuration.getMaxThreads();
    final long partSize;
    if (fileSize < maxThreads * minPartSize) {
      // from 2 to maxThreads parts of size = minPartSize except, possibly, the last
      partSize = minPartSize;

      long residualSize = fileSize % partSize;
      int fullParts = (int)(fileSize / partSize); // fileSize / partSize < maxThreads, safe to case to int
      boolean extendLastFullPart = residualSize < configuration.getMinPartSizeBytesLowerBound(); // don't create new part for too few bytes, extend the last full part

      for (int partNumber = 0; partNumber < fullParts - 1; partNumber++) {
        long startByte = partSize * partNumber;
        long endByte = startByte + partSize - 1;
        parts.add(createPart(partNumber, startByte, endByte, targetFile, downloadContext));
      }

      int lastFullPartNumber = fullParts - 1;
      long lastFullPartStartByte = partSize * lastFullPartNumber;
      long lastFullPartEndByte = extendLastFullPart ? fileSize - 1 : lastFullPartStartByte + partSize - 1;
      parts.add(createPart(lastFullPartNumber, lastFullPartStartByte, lastFullPartEndByte, targetFile, downloadContext));

      if (!extendLastFullPart && residualSize > 0) {
        int lastPartNumber = lastFullPartNumber + 1;
        long lastPartStartByte = partSize * lastPartNumber;
        long lastPartEndByte = fileSize - 1;
        parts.add(createPart(lastPartNumber, lastPartStartByte, lastPartEndByte, targetFile, downloadContext));
      }
    } else {
      // maxThreads parts of size >= minPartSize
      partSize = fileSize / maxThreads;

      for (int partNumber = 0; partNumber < maxThreads; partNumber++) {
        long startByte = partSize * partNumber;
        long endByte = partNumber != maxThreads - 1 ? startByte + partSize - 1 : fileSize - 1;
        parts.add(createPart(partNumber, startByte, endByte, targetFile, downloadContext));
      }
    }

    return parts;
  }

  @NotNull
  protected abstract FilePart createPart(int partNumber, long startByte, long endByte, @NotNull Path targetFile, @NotNull ParallelDownloadContext downloadContext);

  protected abstract void beforeDownloadingParts(@NotNull Path targetFile,
                                                 long fileSize,
                                                 @NotNull List<FilePart> fileParts,
                                                 @NotNull ParallelDownloadState downloadState,
                                                 @NotNull ParallelDownloadContext downloadContext) throws IOException;

  private void downloadPart(@NotNull String srcUrl,
                            @NotNull FilePart filePart,
                            long fileSize,
                            @NotNull ParallelDownloadState downloadState,
                            @NotNull ParallelDownloadContext downloadContext) throws IOException {
    long startByte = filePart.getStartByte();
    long endByte = filePart.getEndByte();
    Path partTarget = filePart.getTargetFile();
    String partDescription = filePart.getDescription();

    LOGGER.debug(String.format("Start downloading part %s to file %s", partDescription, partTarget));
    GetMethod request = null;
    try {
      request = new GetMethod(srcUrl);
      request.addRequestHeader(S3DownloadHttpUtil.createRangeHeader(startByte, endByte));

      checkDownloadInterruptedOrFailed(downloadState);
      int statusCode = downloadContext.getHttpClient().execute(request);

      checkResponseStatus(statusCode, HttpStatus.SC_PARTIAL_CONTENT);
      checkDownloadInterruptedOrFailed(downloadState);
      writePart(request, filePart, fileSize, downloadState, downloadContext);
      LOGGER.debug(String.format("Part %s downloaded to file %s", partDescription, partTarget));
    } catch (IOException | RuntimeException e) {
      LOGGER.debug(String.format("Failed to download part %s to file %s", partDescription, partTarget), e); // debug because there is a general log for the whole file failure
      if (request != null) request.abort(); // aborting the request allows not to wait until full body arrives
      throw new IOException(String.format("Failed to download part %s", partDescription), e);
    } finally {
      if (request != null) request.releaseConnection();
    }
  }

  private void throwFirstFailedPartExceptionIfFailed(@NotNull ParallelDownloadState downloadState) throws IOException {
    PartDownloadFailedException exception = downloadState.getFirstFailedPartException();
    if (exception != null) {
      throw exception;
    }
  }

  protected final void checkDownloadInterruptedOrFailed(@NotNull ParallelDownloadState downloadState) throws IOException {
    checkDownloadInterrupted(downloadState);
    checkDownloadFailed(downloadState);
  }

  protected final void checkDownloadInterrupted(@NotNull ParallelDownloadState downloadState) throws RecoverableIOException {
    if (downloadState.isInterrupted()) {
      throw new RecoverableIOException("Download was interrupted");
    }
  }

  protected final void checkDownloadFailed(@NotNull ParallelDownloadState downloadState) throws IOException {
    if (downloadState.hasFailedParts()) {
      throw new IOException("Download failed in part " + downloadState.getFirstFailedPartNumber(), downloadState.getFirstFailedPartException());
    }
  }

  protected abstract void writePart(@NotNull HttpMethod ongoingRequest,
                                    @NotNull FilePart filePart,
                                    long fileSize,
                                    @NotNull ParallelDownloadState downloadState,
                                    @NotNull ParallelDownloadContext downloadContext) throws IOException;

  protected abstract void afterDownloadingParts(@NotNull Path targetFile,
                                                long fileSize,
                                                @NotNull List<FilePart> fileParts,
                                                @NotNull ParallelDownloadState downloadState,
                                                @NotNull ParallelDownloadContext downloadContext) throws IOException;

  protected abstract void cleanupUnfinishedDownload(@NotNull Path targetFile,
                                                    @NotNull List<FilePart> fileParts,
                                                    @NotNull ParallelDownloadState downloadState,
                                                    @NotNull ParallelDownloadContext downloadContext);
}
