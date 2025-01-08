package jetbrains.buildServer.artifacts.s3.download.parallel;

import org.jetbrains.annotations.NotNull;

public final class FilePart {
  private final int myPartNumber;
  private final long myStartByte;
  private final long myEndByte;

  public FilePart(int partNumber, long startByte, long endByte) {
    myPartNumber = partNumber;
    myStartByte = startByte;
    myEndByte = endByte;
  }

  public int getPartNumber() {
    return myPartNumber;
  }

  public long getStartByte() {
    return myStartByte;
  }

  public long getEndByte() {
    return myEndByte;
  }

  public long getSizeBytes() {
    return myEndByte - myStartByte + 1;
  }

  @NotNull
  public String getDescription() {
    return String.format("%s (bytes %s-%s)", myPartNumber, myStartByte, myEndByte);
  }
}
