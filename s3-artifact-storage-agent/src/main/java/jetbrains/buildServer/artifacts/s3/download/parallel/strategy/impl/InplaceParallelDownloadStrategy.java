package jetbrains.buildServer.artifacts.s3.download.parallel.strategy.impl;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import jetbrains.buildServer.artifacts.s3.download.parallel.FilePart;
import jetbrains.buildServer.artifacts.s3.download.parallel.ParallelDownloadContext;
import jetbrains.buildServer.artifacts.s3.download.parallel.ParallelDownloadState;
import jetbrains.buildServer.util.FileUtil;
import org.apache.commons.httpclient.HttpMethod;
import org.jetbrains.annotations.NotNull;

import static java.nio.file.StandardOpenOption.*;
import static jetbrains.buildServer.artifacts.s3.download.S3DownloadIOUtil.*;

/**
 * Parallel strategy that downloads parts into the target file sectors directly using SeekableByteChannel.
 */
public final class InplaceParallelDownloadStrategy extends AbstractParallelDownloadStrategy {
  public static final String NAME = "INPLACE_PARALLEL";

  @NotNull
  @Override
  protected FilePart createPart(int partNumber, long startByte, long endByte, @NotNull Path targetFile, @NotNull ParallelDownloadContext downloadContext) {
    Path unfinishedTargetFile = getUnfinishedFilePath(targetFile);
    return new FilePart(partNumber, startByte, endByte, unfinishedTargetFile);
  }

  @Override
  protected void beforeDownloadingParts(@NotNull Path targetFile,
                                        long fileSize,
                                        @NotNull List<FilePart> fileParts,
                                        @NotNull ParallelDownloadState downloadState,
                                        @NotNull ParallelDownloadContext downloadContext) throws IOException {
    Path unfinishedTargetFile = getUnfinishedFilePath(targetFile);
    ensureDirectoryExists(unfinishedTargetFile.getParent());
    reserveFileBytes(unfinishedTargetFile, fileSize);
  }

  @Override
  protected void writePart(@NotNull HttpMethod ongoingRequest,
                           @NotNull FilePart filePart,
                           long fileSize,
                           @NotNull ParallelDownloadState downloadState,
                           @NotNull ParallelDownloadContext downloadContext) throws IOException {
    Path partTargetFile = filePart.getTargetFile();
    checkDownloadInterruptedOrFailed(downloadState);
    try (ReadableByteChannel responseBodyChannel = Channels.newChannel(ongoingRequest.getResponseBodyAsStream());
         SeekableByteChannel targetFileChannel = Files.newByteChannel(partTargetFile, WRITE)) {
      transferExpectedBytesToPositionedTarget(
        responseBodyChannel,
        targetFileChannel,
        filePart.getSizeBytes(),
        filePart.getStartByte(),
        downloadContext.getConfiguration().getBufferSizeBytes(),
        () -> checkDownloadInterruptedOrFailed(downloadState),
        (transferred) -> downloadState.addDownloadedBytes(transferred)
      );
    } catch (IOException | RuntimeException e) {
      // aborting the request allows not to wait until full body arrives, this needs to be done before closing the response body stream
      ongoingRequest.abort();
      throw new IOException(String.format("Failed to write part %s to file %s", filePart.getDescription(), partTargetFile), e);
    }
  }

  @Override
  protected void afterDownloadingParts(@NotNull Path targetFile,
                                       long fileSize,
                                       @NotNull List<FilePart> fileParts,
                                       @NotNull ParallelDownloadState downloadState,
                                       @NotNull ParallelDownloadContext downloadContext) throws IOException {
    // rename file
    Path unfinishedTargetFile = getUnfinishedFilePath(targetFile);
    FileUtil.atomicRename(unfinishedTargetFile.toFile(), targetFile.toFile(), 10);
  }

  @Override
  protected void cleanupUnfinishedDownload(@NotNull Path targetFile,
                                           @NotNull List<FilePart> fileParts,
                                           @NotNull ParallelDownloadState downloadState,
                                           @NotNull ParallelDownloadContext downloadContext) {
    Path unfinishedTargetFile = getUnfinishedFilePath(targetFile);
    try {
      checkDownloadInterrupted(downloadState);
      Files.deleteIfExists(unfinishedTargetFile);
      Files.deleteIfExists(targetFile);
    } catch (IOException e) {
      LOGGER.warnAndDebugDetails(String.format("Failed to cleanup unfinished download of file %s: %s", targetFile, e.getMessage()), e);
    }
  }

  @NotNull
  @Override
  public String getName() {
    return NAME;
  }
}
