package jetbrains.buildServer.artifacts.s3.download.parallel.strategy.impl;

import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.artifacts.s3.download.parallel.FilePart;
import jetbrains.buildServer.artifacts.s3.download.parallel.ParallelDownloadContext;
import jetbrains.buildServer.artifacts.s3.download.parallel.ParallelDownloadState;
import jetbrains.buildServer.util.FileUtil;
import org.apache.commons.httpclient.HttpMethod;
import org.jetbrains.annotations.NotNull;

import static java.nio.file.StandardOpenOption.*;
import static jetbrains.buildServer.artifacts.s3.download.S3DownloadIOUtil.*;

/**
 * Parallel strategy that downloads parts into separate files in the build temp directory first, and then merges them into the target file.
 */
public final class SeparatePartFilesParallelDownloadStrategy extends AbstractParallelDownloadStrategy {
  public static final String NAME = "SEPARATE_PART_FILES_PARALLEL";
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
                                        @NotNull List<FilePart> fileParts,
                                        long fileSize,
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
         WritableByteChannel partFileChannel = Files.newByteChannel(partTargetFile, WRITE)) {
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
      throw e;
    }
  }

  @Override
  protected void afterDownloadingParts(@NotNull Path targetFile,
                                       @NotNull List<FilePart> fileParts,
                                       long fileSize,
                                       @NotNull ParallelDownloadState downloadState,
                                       @NotNull ParallelDownloadContext downloadContext) throws IOException {
    try {
      LOGGER.debug(String.format("Restoring file %s from parts", targetFile));
      Path unfinishedTargetFile = getUnfinishedFilePath(targetFile);

      checkDownloadInterrupted(downloadState);
      ensureDirectoryExists(unfinishedTargetFile.getParent());
      reserveFileBytes(unfinishedTargetFile, fileSize);

      Map<Integer, FilePart> partsByNumber = fileParts.stream().collect(Collectors.toMap(part -> part.getPartNumber(), Function.identity()));
      checkDownloadInterrupted(downloadState);
      try (FileChannel targetFileChannel = FileChannel.open(unfinishedTargetFile, WRITE)) {
        long totalCopied = 0L;
        for (int partNumber = 0; partNumber < fileParts.size(); partNumber++) {
          FilePart filePart = partsByNumber.get(partNumber);
          Objects.requireNonNull(filePart, String.format("Part retrieved by number %s is null, some parts missing?", partNumber));
          copyPart(filePart, targetFileChannel, downloadState);
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

  private void copyPart(@NotNull FilePart filePart, @NotNull FileChannel targetFileChannel, @NotNull ParallelDownloadState downloadState) throws IOException {
    Path partFile = filePart.getTargetFile();
    checkDownloadInterrupted(downloadState);
    try (FileChannel partFileChannel = FileChannel.open(partFile, READ)) {
      targetFileChannel.position(filePart.getStartByte());
      transferExpectedFileBytes(
        partFileChannel,
        targetFileChannel,
        filePart.getSizeBytes(),
        () -> checkDownloadInterrupted(downloadState),
        (transferred) -> {
        }
      );
    } catch (IOException | RuntimeException e) {
      throw new IOException(String.format("Failed to copy part %s from %s", filePart.getDescription(), partFile), e);
    }
  }

  @Override
  protected void cleanupUnfinishedDownload(@NotNull Path targetFile,
                                           @NotNull List<FilePart> fileParts,
                                           @NotNull ParallelDownloadState downloadState,
                                           @NotNull ParallelDownloadContext downloadContext) throws IOException {
    Path unfinishedTargetFile = getUnfinishedFilePath(targetFile);
    Files.deleteIfExists(unfinishedTargetFile);
    Files.deleteIfExists(targetFile);
    for (FilePart filePart : fileParts) {
      Files.deleteIfExists(filePart.getTargetFile());
    }
  }

  @NotNull
  @Override
  public String getName() {
    return NAME;
  }
}
