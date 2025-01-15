package jetbrains.buildServer.artifacts.s3.download.parallel.strategy.impl;

import com.intellij.openapi.util.TCSystemInfo;
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
public class InplaceParallelDownloadStrategy extends AbstractParallelDownloadStrategy {
  public static final String NAME = "INPLACE_PARALLEL";

  @Override
  protected void beforeDownloadingParts(@NotNull Path targetFile,
                                        @NotNull List<FilePart> fileParts,
                                        long fileSize,
                                        @NotNull ParallelDownloadState downloadState,
                                        @NotNull ParallelDownloadContext downloadContext) throws IOException {
    Path unfinishedTargetFile = getUnfinishedFilePath(targetFile);
    ensureDirectoryExists(unfinishedTargetFile.getParent());

    // we need the file to be sparse on Windows, because writes to far positions of an empty regular file
    // block for a long period of time which leads to connection reset by the server on large files
    createFile(unfinishedTargetFile, TCSystemInfo.isWindows);
  }

  @Override
  protected void writePart(@NotNull HttpMethod ongoingRequest,
                           @NotNull FilePart filePart,
                           @NotNull Path targetFile,
                           @NotNull ParallelDownloadState downloadState,
                           @NotNull ParallelDownloadContext downloadContext) throws IOException {
    Path partTargetFile = getUnfinishedFilePath(targetFile);
    checkDownloadInterruptedOrFailed(downloadState);
    try (ReadableByteChannel responseBodyChannel = Channels.newChannel(ongoingRequest.getResponseBodyAsStream());
         SeekableByteChannel targetFileChannel = Files.newByteChannel(partTargetFile, WRITE)) {
      targetFileChannel.position(filePart.getStartByte());
      transferExpectedBytes(
        responseBodyChannel,
        targetFileChannel,
        filePart.getSizeBytes(),
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
    Path unfinishedTargetFile = getUnfinishedFilePath(targetFile);
    FileUtil.atomicRename(unfinishedTargetFile.toFile(), targetFile.toFile(), 10);
  }

  @Override
  protected void cleanupUnfinishedDownload(@NotNull Path targetFile,
                                           @NotNull List<FilePart> fileParts,
                                           @NotNull ParallelDownloadState downloadState,
                                           @NotNull ParallelDownloadContext downloadContext) throws IOException {
    Path unfinishedTargetFile = getUnfinishedFilePath(targetFile);
    Files.deleteIfExists(unfinishedTargetFile);
    Files.deleteIfExists(targetFile);
  }

  @NotNull
  @Override
  public String getName() {
    return NAME;
  }
}
