package jetbrains.buildServer.artifacts.s3.download.parallel.strategy.impl;

import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.artifacts.s3.download.parallel.ParallelStrategyType;
import jetbrains.buildServer.artifacts.s3.download.parallel.FilePart;
import jetbrains.buildServer.artifacts.s3.download.parallel.ParallelDownloadContext;
import jetbrains.buildServer.artifacts.s3.download.parallel.ParallelDownloadState;
import jetbrains.buildServer.util.FileUtil;
import org.apache.commons.httpclient.HttpMethod;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.artifacts.s3.download.S3DownloadIOUtil.*;

/**
 * Parallel strategy that downloads parts into separate files in the build temp directory first, and then merges them into the target file.
 */
public final class SeparatePartFilesParallelDownloadStrategy extends AbstractParallelDownloadStrategy {
  private static final String BUILD_TEMP_SUBDIRECTORY_NAME = "s3_downloads";

  @NotNull
  @Override
  protected FilePart createPart(int partNumber, long startByte, long endByte, @NotNull Path targetFile, @NotNull ParallelDownloadContext downloadContext) {
    String targetFileName = targetFile.getFileName().toString();
    Path tempPartsDirectory = getTempPartsDirectory(downloadContext.getRunningBuild());
    Path partTargetFile = tempPartsDirectory.resolve(targetFileName + ".part." + partNumber);
    return new FilePart(partNumber, startByte, endByte, partTargetFile);
  }

  @NotNull
  private Path getTempPartsDirectory(@NotNull AgentRunningBuild runningBuild) {
    return runningBuild.getBuildTempDirectory().toPath().resolve(BUILD_TEMP_SUBDIRECTORY_NAME);
  }

  @Override
  protected void beforeDownloadingParts(@NotNull Path targetFile,
                                        long fileSize,
                                        @NotNull List<FilePart> fileParts,
                                        @NotNull ParallelDownloadState downloadState,
                                        @NotNull ParallelDownloadContext downloadContext) throws IOException {
    Path tempPartsDirectory = getTempPartsDirectory(downloadContext.getRunningBuild());
    checkDownloadInterruptedOrFailed(downloadState);
    ensureDirectoryExists(tempPartsDirectory);
  }

  @Override
  protected void writePart(@NotNull HttpMethod ongoingRequest,
                           @NotNull FilePart filePart,
                           @NotNull ParallelDownloadState downloadState,
                           @NotNull ParallelDownloadContext downloadContext) throws IOException {
    Path partTargetFile = filePart.getTargetFile();
    long partSizeBytes = filePart.getSizeBytes();
    checkDownloadInterruptedOrFailed(downloadState);
    reserveFileBytes(partTargetFile, partSizeBytes);
    try (ReadableByteChannel responseBodyChannel = Channels.newChannel(ongoingRequest.getResponseBodyAsStream());
         WritableByteChannel partFileChannel = Files.newByteChannel(partTargetFile, StandardOpenOption.WRITE)) {
      transferExpectedBytes(
        responseBodyChannel,
        partFileChannel,
        partSizeBytes,
        downloadContext.getConfiguration().getBufferSizeBytes(),
        () -> checkDownloadInterruptedOrFailed(downloadState),
        (transferred) -> downloadState.addDownloadedBytes(transferred)
      );
    } catch (IOException | RuntimeException e) {
      // aborting the request allows not to wait until full body arrives, this needs to be done before closing the response body stream
      ongoingRequest.abort();
      throw new IOException(String.format("Failed to write part %s-%s to file %s", filePart.getStartByte(), filePart.getEndByte(), partTargetFile), e);
    }
  }

  @Override
  protected void afterDownloadingParts(@NotNull Path targetFile,
                                       long fileSize,
                                       @NotNull List<FilePart> fileParts,
                                       @NotNull ParallelDownloadState downloadState,
                                       @NotNull ParallelDownloadContext downloadContext)
    throws IOException {
    try {
      LOGGER.debug(String.format("Restoring file %s from parts", targetFile));
      Path unfinishedTargetFile = getUnfinishedFilePath(targetFile);

      checkDownloadInterrupted(downloadState);
      ensureDirectoryExists(unfinishedTargetFile.getParent());
      reserveFileBytes(unfinishedTargetFile, fileSize);

      Map<Integer, FilePart> partsByNumber = fileParts.stream().collect(Collectors.toMap(part -> part.getPartNumber(), Function.identity()));
      checkDownloadInterrupted(downloadState);
      try (FileChannel targetFileChannel = FileChannel.open(unfinishedTargetFile, StandardOpenOption.WRITE)) {
        long totalCopied = 0L;
        for (int partNumber = 0; partNumber < fileParts.size(); partNumber++) {
          FilePart filePart = partsByNumber.get(partNumber);
          Objects.requireNonNull(filePart, String.format("Part retrieved by number %s is null, some parts missing?", partNumber));

          checkDownloadInterrupted(downloadState);
          copyPart(filePart, targetFileChannel, downloadState, downloadContext);
          totalCopied += filePart.getSizeBytes();
        }

        if (totalCopied < fileSize) throw new IOException(String.format("Merged parts contain less bytes (%s) than expected (%s)", totalCopied, fileParts));
      }

      // rename file and delete parts
      checkDownloadInterrupted(downloadState);
      FileUtil.atomicRename(unfinishedTargetFile.toFile(), targetFile.toFile(), 10);
      for (FilePart filePart : fileParts) {
        checkDownloadInterrupted(downloadState);
        Files.deleteIfExists(filePart.getTargetFile());
      }

      LOGGER.debug(String.format("Restored file %s from parts", targetFile));
    } catch (IOException | RuntimeException e) {
      LOGGER.debug(String.format("Failed to restore file %s from parts", targetFile), e);
      throw e;
    }
  }

  private void copyPart(@NotNull FilePart filePart,
                        @NotNull FileChannel targetFileChannel,
                        @NotNull ParallelDownloadState downloadState,
                        @NotNull ParallelDownloadContext downloadContext) throws IOException {
    Path partFile = filePart.getTargetFile();
    try (FileChannel partFileChannel = FileChannel.open(partFile, StandardOpenOption.READ)) {
      transferExpectedBytesToPositionedTarget(
        partFileChannel,
        targetFileChannel,
        filePart.getStartByte(),
        filePart.getSizeBytes(),
        downloadContext.getConfiguration().getBufferSizeBytes(),
        () -> checkDownloadInterrupted(downloadState),
        (transferred) -> {
        }
      );
    } catch (IOException | RuntimeException e) {
      throw new IOException(String.format("Failed to copy part %s-%s from %s", filePart.getStartByte(), filePart.getEndByte(), partFile), e);
    }
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
      for (FilePart filePart : fileParts) {
        checkDownloadInterrupted(downloadState);
        Files.deleteIfExists(filePart.getTargetFile());
      }
    } catch (IOException e) {
      LOGGER.warnAndDebugDetails(String.format("Failed to cleanup unfinished download of file %s: %s", targetFile, e.getMessage()), e);
    }
  }

  @NotNull
  @Override
  public String getName() {
    return ParallelStrategyType.SEPARATE_PART_FILES_PARALLEL.name();
  }
}
