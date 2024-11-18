package jetbrains.buildServer.artifacts.s3.download.strategy;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import jetbrains.buildServer.artifacts.FileProgress;
import jetbrains.buildServer.artifacts.s3.download.S3HttpClient;
import jetbrains.buildServer.artifacts.s3.download.S3DownloadConfiguration;
import jetbrains.buildServer.util.FileUtil;
import org.apache.commons.httpclient.HttpMethod;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.artifacts.s3.download.S3DownloadFileUtil.*;

/**
 * Parallel downloader that downloads parts into the target file directly using SeekableByteChannel.
 */
public final class InplaceParallelDownloadStrategy extends ParallelDownloadStrategy {

  public InplaceParallelDownloadStrategy(@NotNull S3HttpClient httpClient,
                                         @NotNull ExecutorService executorService,
                                         @NotNull S3DownloadConfiguration downloadConfiguration,
                                         @NotNull AtomicBoolean isInterrupted,
                                         @NotNull FileProgress externalDownloadProgress) {
    super(httpClient, executorService, downloadConfiguration, isInterrupted, externalDownloadProgress);
  }

  @NotNull
  @Override
  protected FilePart createPart(int partNumber, long startByte, long endByte, @NotNull Path targetFile) {
    Path unfinishedTargetFile = getUnfinishedFilePath(targetFile);
    return new FilePart(partNumber, startByte, endByte, unfinishedTargetFile);
  }

  @Override
  protected void beforeDownloadingParts(@NotNull Path targetFile, long fileSizeBytes, @NotNull List<FilePart> fileParts, @NotNull ParallelDownloadState downloadState)
    throws IOException {
    Path unfinishedTargetFile = getUnfinishedFilePath(targetFile);
    ensureDirectoryExists(unfinishedTargetFile.getParent());
    reserveBytes(unfinishedTargetFile, fileSizeBytes);
  }

  @Override
  protected void writePart(@NotNull HttpMethod ongoingRequest, @NotNull FilePart filePart, @NotNull ParallelDownloadState downloadState) throws IOException {
    Path partTargetFile = filePart.getTargetFile();
    long startByte = filePart.getStartByte();
    checkIfInterruptedOrOtherPartFailed(downloadState);
    try (ReadableByteChannel responseBodyChannel = Channels.newChannel(ongoingRequest.getResponseBodyAsStream());
         SeekableByteChannel targetFileChannel = Files.newByteChannel(partTargetFile, StandardOpenOption.WRITE)) {
      transferExpectedBytesToPositionedTarget(
        responseBodyChannel,
        targetFileChannel,
        startByte,
        filePart.getSizeBytes(),
        myDownloadConfiguration.getBufferSizeBytes(),
        () -> checkIfInterruptedOrOtherPartFailed(downloadState),
        (transferred) -> downloadState.addDownloadedBytes(transferred)
      );
    } catch (IOException | RuntimeException e) {
      // aborting the request allows not to wait until full body arrives, this needs to be done before closing the response body stream
      ongoingRequest.abort();
      throw new IOException(String.format("Failed to write part %s-%s to file %s", startByte, filePart.getEndByte(), partTargetFile), e);
    }
  }

  @Override
  protected void afterDownloadingParts(@NotNull Path targetFile, long fileSizeBytes, @NotNull List<FilePart> fileParts, @NotNull ParallelDownloadState downloadState)
    throws IOException {
    // rename file
    Path unfinishedTargetFile = getUnfinishedFilePath(targetFile);
    FileUtil.atomicRename(unfinishedTargetFile.toFile(), targetFile.toFile(), 10);
  }

  @Override
  protected void cleanupUnfinishedDownload(@NotNull Path targetFile, @NotNull List<FilePart> fileParts, @NotNull ParallelDownloadState downloadState) {
    Path unfinishedTargetFile = getUnfinishedFilePath(targetFile);
    try {
      checkIfInterrupted(downloadState);
      Files.deleteIfExists(unfinishedTargetFile);
      Files.deleteIfExists(targetFile);
    } catch (IOException e) {
      LOGGER.warnAndDebugDetails(String.format("Failed to cleanup unfinished download of file %s: %s", targetFile, e.getMessage()), e);
    }
  }

  @NotNull
  @Override
  public String getName() {
    return FileDownloadStrategyType.INPLACE_PARALLEL.name();
  }
}
