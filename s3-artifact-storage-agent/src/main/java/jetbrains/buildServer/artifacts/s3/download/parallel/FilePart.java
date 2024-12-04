package jetbrains.buildServer.artifacts.s3.download.parallel;

import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;

public final class FilePart {
  private final int partNumber;
  private final long startByte;
  private final long endByte;
  @NotNull
  private final Path targetFile;

  public FilePart(int partNumber, long startByte, long endByte, @NotNull Path targetFile) {
    this.partNumber = partNumber;
    this.startByte = startByte;
    this.endByte = endByte;
    this.targetFile = targetFile;
  }

  public int getPartNumber() {
    return partNumber;
  }

  public long getStartByte() {
    return startByte;
  }

  public long getEndByte() {
    return endByte;
  }

  public long getSizeBytes() {
    return endByte - startByte + 1;
  }

  @NotNull
  public Path getTargetFile() {
    return targetFile;
  }
}
