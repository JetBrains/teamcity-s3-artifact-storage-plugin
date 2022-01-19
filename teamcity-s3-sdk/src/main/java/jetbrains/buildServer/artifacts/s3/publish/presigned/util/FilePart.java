package jetbrains.buildServer.artifacts.s3.publish.presigned.util;

import org.jetbrains.annotations.Nullable;

public class FilePart {
  private final long start;
  private final long length;
  private final int partNumber;
  private final String digest;

  public FilePart(long start, long length, int partNumber, @Nullable String digest) {
    this.start = start;
    this.length = length;
    this.partNumber = partNumber;
    this.digest = digest;
  }

  public long getStart() {
    return start;
  }

  public long getLength() {
    return length;
  }

  public int getPartNumber() {
    return partNumber;
  }

  @Nullable
  public String getDigest() {
    return digest;
  }
}
