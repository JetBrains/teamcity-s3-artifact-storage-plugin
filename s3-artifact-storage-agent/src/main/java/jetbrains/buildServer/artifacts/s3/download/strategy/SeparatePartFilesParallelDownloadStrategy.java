package jetbrains.buildServer.artifacts.s3.download.strategy;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import jetbrains.buildServer.artifacts.FileProgress;
import jetbrains.buildServer.artifacts.s3.download.S3HttpClient;
import jetbrains.buildServer.artifacts.s3.download.S3DownloadConfiguration;
import jetbrains.buildServer.util.FileUtil;
import org.apache.commons.httpclient.HttpMethod;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.artifacts.s3.download.S3DownloadFileUtil.*;

/**
 * Parallel downloader that downloads parts into separate files in the build temp directory first, and then merges them into the target file.
 */
public final class SeparatePartFilesParallelDownloadStrategy extends ParallelDownloadStrategy {
  private static final String BUILD_TEMP_SUBDIRECTORY_NAME = "s3_downloads";
  private final Path downloadedPartsDirectory;

  public SeparatePartFilesParallelDownloadStrategy(@NotNull S3HttpClient httpClient,
                                                   @NotNull ExecutorService executorService,
                                                   @NotNull S3DownloadConfiguration downloadConfiguration,
                                                   @NotNull AtomicBoolean isInterrupted,
                                                   @NotNull FileProgress externalDownloadProgress) {
    super(httpClient, executorService, downloadConfiguration, isInterrupted, externalDownloadProgress);
    downloadedPartsDirectory = downloadConfiguration.getBuildTempDirectory().resolve(BUILD_TEMP_SUBDIRECTORY_NAME);
  }

  @NotNull
  @Override
  protected FilePart createPart(int partNumber, long startByte, long endByte, @NotNull Path targetFile) {
    String targetFileName = targetFile.getFileName().toString();
    Path partTargetFile = downloadedPartsDirectory.resolve(targetFileName + ".part." + partNumber);
    return new FilePart(partNumber, startByte, endByte, partTargetFile);
  }

  @Override
  protected void beforeDownloadingParts(@NotNull Path targetFile, long fileSizeBytes, @NotNull List<FilePart> fileParts, @NotNull ParallelDownloadState downloadState) {
    // no need to do anything
  }

  @Override
  protected void writePart(@NotNull HttpMethod ongoingRequest, @NotNull FilePart filePart, @NotNull ParallelDownloadState downloadState) throws IOException {
    Path partTargetFile = filePart.getTargetFile();
    long partSizeBytes = filePart.getSizeBytes();
    checkIfInterruptedOrOtherPartFailed(downloadState);
    ensureDirectoryExists(downloadedPartsDirectory);
    reserveBytes(partTargetFile, partSizeBytes);
    try (ReadableByteChannel responseBodyChannel = Channels.newChannel(ongoingRequest.getResponseBodyAsStream());
         WritableByteChannel partFileChannel = Files.newByteChannel(partTargetFile, StandardOpenOption.WRITE)) {
      transferExpectedBytes(
        responseBodyChannel,
        partFileChannel,
        partSizeBytes,
        myDownloadConfiguration.getBufferSizeBytes(),
        () -> checkIfInterruptedOrOtherPartFailed(downloadState),
        (transferred) -> downloadState.addDownloadedBytes(transferred)
      );
    } catch (IOException | RuntimeException e) {
      // aborting the request allows not to wait until full body arrives, this needs to be done before closing the response body stream
      ongoingRequest.abort();
      throw new IOException(String.format("Failed to write part %s-%s to file %s", filePart.getStartByte(), filePart.getEndByte(), partTargetFile), e);
    }
  }

  @Override
  protected void afterDownloadingParts(@NotNull Path targetFile, long fileSizeBytes, @NotNull List<FilePart> fileParts, @NotNull ParallelDownloadState downloadState)
    throws IOException {
    try {
      LOGGER.debug(String.format("Restoring file %s from parts", targetFile));
      Path unfinishedTargetFile = getUnfinishedFilePath(targetFile);

      checkIfInterrupted(downloadState);
      ensureDirectoryExists(unfinishedTargetFile.getParent());
      reserveBytes(unfinishedTargetFile, fileSizeBytes);

      Map<Integer, FilePart> partsByNumber = fileParts.stream().collect(Collectors.toMap(part -> part.getPartNumber(), Function.identity()));
      checkIfInterrupted(downloadState);
      try (SeekableByteChannel targetFileChannel = Files.newByteChannel(unfinishedTargetFile, StandardOpenOption.WRITE)) {
        long totalCopied = 0L;
        for (int partNumber = 0; partNumber < fileParts.size(); partNumber++) {
          FilePart filePart = partsByNumber.get(partNumber);
          Objects.requireNonNull(filePart, String.format("Part retrieved by number %s is null, some parts missing?", partNumber));

          checkIfInterrupted(downloadState);
          copyPart(filePart, targetFileChannel, downloadState);
          totalCopied += filePart.getSizeBytes();
        }

        if (totalCopied < fileSizeBytes) throw new IOException(String.format("Merged parts contain less bytes (%s) than expected (%s)", totalCopied, fileParts));
      }

      // rename file and delete parts
      checkIfInterrupted(downloadState);
      FileUtil.atomicRename(unfinishedTargetFile.toFile(), targetFile.toFile(), 10);
      for (FilePart filePart : fileParts) {
        checkIfInterrupted(downloadState);
        Files.deleteIfExists(filePart.getTargetFile());
      }

      LOGGER.debug(String.format("Restored file %s from parts", targetFile));
    } catch (IOException | RuntimeException e) {
      LOGGER.debug(String.format("Failed to restore file %s from parts", targetFile), e);
      throw e;
    }
  }

  private void copyPart(@NotNull FilePart filePart, @NotNull SeekableByteChannel targetFileChannel, @NotNull ParallelDownloadState downloadState) throws IOException {
    Path partFile = filePart.getTargetFile();
    try (ReadableByteChannel partFileChannel = Files.newByteChannel(partFile)) {
      transferExpectedBytesToPositionedTarget(
        partFileChannel,
        targetFileChannel,
        filePart.getStartByte(),
        filePart.getSizeBytes(),
        myDownloadConfiguration.getBufferSizeBytes(),
        () -> checkIfInterrupted(downloadState),
        (transferred) -> {
        }
      );
    } catch (IOException | RuntimeException e) {
      throw new IOException(String.format("Failed to copy part %s-%s from %s", filePart.getStartByte(), filePart.getEndByte(), partFile), e);
    }
  }

  @Override
  protected void cleanupUnfinishedDownload(@NotNull Path targetFile, @NotNull List<FilePart> fileParts, @NotNull ParallelDownloadState downloadState) {
    Path unfinishedTargetFile = getUnfinishedFilePath(targetFile);
    try {
      checkIfInterrupted(downloadState);
      Files.deleteIfExists(unfinishedTargetFile);
      Files.deleteIfExists(targetFile);
      for (FilePart filePart : fileParts) {
        checkIfInterrupted(downloadState);
        Files.deleteIfExists(filePart.getTargetFile());
      }
    } catch (IOException e) {
      LOGGER.warnAndDebugDetails(String.format("Failed to cleanup unfinished download of file %s: %s", targetFile, e.getMessage()), e);
    }
  }

  @NotNull
  @Override
  public String getName() {
    return FileDownloadStrategyType.SEPARATE_PART_FILES_PARALLEL.name();
  }
}
