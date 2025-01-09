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
import jetbrains.buildServer.artifacts.s3.download.parallel.*;
import jetbrains.buildServer.artifacts.s3.download.parallel.splitter.FileSplitter;
import jetbrains.buildServer.artifacts.s3.download.parallel.strategy.ParallelDownloadStrategy;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.artifacts.s3.download.S3DownloadHttpUtil.checkResponseStatus;
import static jetbrains.buildServer.artifacts.s3.download.S3DownloadHttpUtil.createRangeHeader;

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
    FileSplitter fileSplitter = downloadContext.getFileSplitter();

    List<FilePart> fileParts = fileSplitter.split(fileSize);
    if (fileParts.size() == 1) {
      LOGGER.warn(String.format("File %s of size %s should be downloaded in parallel, but was split into only 1 part %s. S3 artifact transport misconfigured?",
                                targetFile, fileSize, fileParts.get(0).getDescription()));
    }

    LOGGER.debug(String.format(
      "Start downloading file %s of size %s from %s in %s parts of min %s MB each (except, possibly, the last) by max %s threads", // MB in logs because not less than 1 MB
      targetFile, fileSize, srcUrl, fileParts.size(), configuration.getMinPartSizeBytes() / (1024 * 1024), Math.min(configuration.getMaxThreads(), fileParts.size())
    ));
    ParallelDownloadState downloadState = new ParallelDownloadState(downloadProgress, downloadContext.getInterruptedFlag());
    try {
      try {
        checkDownloadInterrupted(downloadState);
        beforeDownloadingParts(targetFile, fileParts, fileSize, downloadState, downloadContext);
        LOGGER.debug("Finished preparations before downloading parts of file " + targetFile);
      } catch (Exception e) {
        throw new IOException("Preparations before downloading parts failed", e);
      }

      try {
        checkDownloadInterrupted(downloadState);
        downloadParts(srcUrl, fileParts, targetFile, fileSize, downloadState, downloadContext);
        LOGGER.debug("Finished downloading parts of file " + targetFile);
      } catch (Exception e) {
        throw new IOException("Failed to download file parts", e);
      }

      try {
        checkDownloadInterrupted(downloadState);
        afterDownloadingParts(targetFile, fileParts, fileSize, downloadState, downloadContext);
        LOGGER.debug("Finished post-processing after downloading parts of file " + targetFile);
      } catch (Exception e) {
        throw new IOException("Post-processing after downloading parts failed", e);
      }
    } catch (IOException downloadException) {
      try {
        cleanupUnfinishedDownload(targetFile, fileParts, downloadState, downloadContext);
      } catch (Exception cleanupException) {
        LOGGER.warnAndDebugDetails(String.format("Failed to cleanup unfinished download of file %s: %s", targetFile, cleanupException.getMessage()), cleanupException);
      }

      throw downloadException;
    }
  }

  protected void beforeDownloadingParts(@NotNull Path targetFile,
                                        @NotNull List<FilePart> fileParts,
                                        long fileSize,
                                        @NotNull ParallelDownloadState downloadState,
                                        @NotNull ParallelDownloadContext downloadContext) throws IOException {
  }

  private void downloadParts(@NotNull String srcUrl,
                             @NotNull List<FilePart> fileParts,
                             @NotNull Path targetFile,
                             long fileSize,
                             @NotNull ParallelDownloadState downloadState,
                             @NotNull ParallelDownloadContext downloadContext) throws IOException {
    downloadState.expectDownloadedBytes(fileSize);
    List<CompletableFuture<Void>> partDownloadFutures = new CopyOnWriteArrayList<>();
    fileParts.stream()
      .map(filePart -> {
        if (downloadState.isInterrupted() || downloadState.hasFailedParts()) return null;
        return CompletableFuture.runAsync(() -> {
          try {
            String partDescription = filePart.getDescription();
            LOGGER.debug(String.format("Start downloading part %s", partDescription));
            downloadPart(srcUrl, filePart, targetFile, downloadState, downloadContext);
            LOGGER.debug(String.format("Part %s downloaded", partDescription));
          } catch (Exception e) {
            LOGGER.debug(String.format("Failed to download part %s: %s", filePart.getDescription(), e.getMessage()), e);
            downloadState.partFailed(filePart, new IOException("Failed to download part " + filePart.getDescription(), e));
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
      // rethrow Error from executor or downloadPart method
      Throwable somePartException = allPartsDownloadException.getCause();
      Throwable somePartExceptionCause = somePartException.getCause();
      if (somePartException instanceof Error) throw (Error)somePartException;
      if (somePartExceptionCause != null && somePartExceptionCause instanceof Error) throw (Error)somePartExceptionCause;

      // exception is from executor
      // it might be a cancellation exception if some part failed, throw this part's exception instead
      rethrowPartExceptionIfDownloadFailed(downloadState);

      // this happens when executor fails to run any of the part tasks (e.g. executor shut down)
      throw new IOException("Failed to start all part downloads", somePartException);
    }

    // check for caught part download exceptions
    rethrowPartExceptionIfDownloadFailed(downloadState);
  }

  private void downloadPart(@NotNull String srcUrl,
                            @NotNull FilePart filePart,
                            @NotNull Path targetFile,
                            @NotNull ParallelDownloadState downloadState,
                            @NotNull ParallelDownloadContext downloadContext) throws IOException {
    GetMethod request = null;
    try {
      request = new GetMethod(srcUrl);
      request.addRequestHeader(createRangeHeader(filePart.getStartByte(), filePart.getEndByte()));

      checkDownloadInterruptedOrFailed(downloadState);
      int statusCode = downloadContext.getHttpClient().execute(request);

      checkResponseStatus(statusCode, HttpStatus.SC_PARTIAL_CONTENT);
      checkDownloadInterruptedOrFailed(downloadState);
      writePart(request, filePart, targetFile, downloadState, downloadContext);
    } catch (IOException | RuntimeException e) {
      if (request != null) request.abort();
      throw e;
    } finally {
      if (request != null) request.releaseConnection();
    }
  }

  protected abstract void writePart(@NotNull HttpMethod ongoingRequest,
                                    @NotNull FilePart filePart,
                                    @NotNull Path targetFile,
                                    @NotNull ParallelDownloadState downloadState,
                                    @NotNull ParallelDownloadContext downloadContext) throws IOException;

  protected void afterDownloadingParts(@NotNull Path targetFile,
                                       @NotNull List<FilePart> fileParts,
                                       long fileSize,
                                       @NotNull ParallelDownloadState downloadState,
                                       @NotNull ParallelDownloadContext downloadContext) throws IOException {
  }

  protected abstract void cleanupUnfinishedDownload(@NotNull Path targetFile,
                                                    @NotNull List<FilePart> fileParts,
                                                    @NotNull ParallelDownloadState downloadState,
                                                    @NotNull ParallelDownloadContext downloadContext) throws IOException;

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
      PartFailure firstPartFailure = downloadState.getFirstPartFailure();
      Objects.requireNonNull(firstPartFailure, "First part failure is null");
      throw new IOException("Download failed in part " + firstPartFailure.getPart().getDescription(), firstPartFailure.getException());
    }
  }

  private void rethrowPartExceptionIfDownloadFailed(@NotNull ParallelDownloadState downloadState) throws IOException {
    if (downloadState.hasFailedParts()) {
      PartFailure firstPartFailure = downloadState.getFirstPartFailure();
      Objects.requireNonNull(firstPartFailure, "First part failure is null");
      throw firstPartFailure.getException();
    }
  }
}
