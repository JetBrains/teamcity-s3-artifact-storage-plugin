package jetbrains.buildServer.artifacts.s3.download.strategy;

import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;

final class FilePart {
  private final int partNumber;
  private final long startByte;
  private final long endByte;
  @NotNull
  private final Path targetFile;

  FilePart(int partNumber, long startByte, long endByte, @NotNull Path targetFile) {
    this.partNumber = partNumber;
    this.startByte = startByte;
    this.endByte = endByte;
    this.targetFile = targetFile;
  }

  int getPartNumber() {
    return partNumber;
  }

  long getStartByte() {
    return startByte;
  }

  long getEndByte() {
    return endByte;
  }

  long getSizeBytes() {
    return endByte - startByte + 1;
  }

  @NotNull
  Path getTargetFile() {
    return targetFile;
  }
}
