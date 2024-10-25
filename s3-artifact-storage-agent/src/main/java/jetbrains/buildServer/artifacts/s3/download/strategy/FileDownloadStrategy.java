package jetbrains.buildServer.artifacts.s3.download.strategy;

import java.io.IOException;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;

public interface FileDownloadStrategy {
  void download(String srcUrl, Path targetFile, long fileSizeBytes) throws IOException;

  @NotNull
  String getName();
}
