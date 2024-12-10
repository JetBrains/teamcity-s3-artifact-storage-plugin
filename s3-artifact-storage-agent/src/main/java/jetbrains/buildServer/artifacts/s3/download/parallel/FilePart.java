package jetbrains.buildServer.artifacts.s3.download.parallel;

import org.jetbrains.annotations.NotNull;

public final class FilePart {
  private final int partNumber;
  private final long startByte;
  private final long endByte;

  public FilePart(int partNumber, long startByte, long endByte) {
    this.partNumber = partNumber;
    this.startByte = startByte;
    this.endByte = endByte;
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
  public String getDescription() {
    return String.format("%s (bytes %s-%s)", partNumber, startByte, endByte);
  }
}
