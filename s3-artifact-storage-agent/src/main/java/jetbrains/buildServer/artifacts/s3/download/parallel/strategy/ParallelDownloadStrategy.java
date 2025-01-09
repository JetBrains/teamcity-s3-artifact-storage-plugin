package jetbrains.buildServer.artifacts.s3.download.parallel.strategy;

import java.io.IOException;
import java.nio.file.Path;
import jetbrains.buildServer.artifacts.FileProgress;
import jetbrains.buildServer.artifacts.s3.download.parallel.ParallelDownloadContext;
import org.jetbrains.annotations.NotNull;

public interface ParallelDownloadStrategy {
  void download(@NotNull String srcUrl,
                @NotNull Path targetFile,
                long fileSize,
                @NotNull FileProgress downloadProgress,
                @NotNull ParallelDownloadContext downloadContext) throws IOException;

  @NotNull
  String getName();
}
