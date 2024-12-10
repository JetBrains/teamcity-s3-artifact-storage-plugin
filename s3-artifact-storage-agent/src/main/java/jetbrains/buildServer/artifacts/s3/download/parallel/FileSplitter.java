package jetbrains.buildServer.artifacts.s3.download.parallel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jetbrains.buildServer.artifacts.s3.download.S3DownloadConfiguration;
import org.jetbrains.annotations.NotNull;

public final class FileSplitter {
  @NotNull
  private final S3DownloadConfiguration configuration;

  public FileSplitter(@NotNull S3DownloadConfiguration configuration) {
    this.configuration = configuration;
  }

  @NotNull
  public List<FilePart> splitIntoParts(long fileSize) {
    final long splitThreshold = getSplitThreshold();
    if (fileSize < splitThreshold) {
      // only one part of size fileSize
      return Collections.singletonList(new FilePart(0, 0, fileSize - 1));
    }

    List<FilePart> parts = new ArrayList<>();
    final long minPartSize = configuration.getMinPartSizeBytes();
    final int maxThreads = configuration.getMaxThreads();
    final long partSize;
    if (fileSize < maxThreads * minPartSize) {
      // from 2 to maxThreads parts of size = minPartSize except, possibly, the last
      partSize = minPartSize;

      long residualSize = fileSize % partSize;
      int fullParts = (int)(fileSize / partSize); // fileSize / partSize < maxThreads, safe to case to int
      boolean extendLastFullPart = residualSize < configuration.getMinPartSizeBytesLowerBound(); // don't create new part for too few bytes, extend the last full part

      for (int partNumber = 0; partNumber < fullParts - 1; partNumber++) {
        long startByte = partSize * partNumber;
        long endByte = startByte + partSize - 1;
        parts.add(new FilePart(partNumber, startByte, endByte));
      }

      int lastFullPartNumber = fullParts - 1;
      long lastFullPartStartByte = partSize * lastFullPartNumber;
      long lastFullPartEndByte = extendLastFullPart ? fileSize - 1 : lastFullPartStartByte + partSize - 1;
      parts.add(new FilePart(lastFullPartNumber, lastFullPartStartByte, lastFullPartEndByte));

      if (!extendLastFullPart && residualSize > 0) {
        int lastPartNumber = lastFullPartNumber + 1;
        long lastPartStartByte = partSize * lastPartNumber;
        long lastPartEndByte = fileSize - 1;
        parts.add(new FilePart(lastPartNumber, lastPartStartByte, lastPartEndByte));
      }
    } else {
      // maxThreads parts of size >= minPartSize
      partSize = fileSize / maxThreads;

      for (int partNumber = 0; partNumber < maxThreads; partNumber++) {
        long startByte = partSize * partNumber;
        long endByte = partNumber != maxThreads - 1 ? startByte + partSize - 1 : fileSize - 1;
        parts.add(new FilePart(partNumber, startByte, endByte));
      }
    }

    return parts;
  }

  public long getSplitThreshold() {
    // there should be at least the lower bound for minimum part size bytes left for the second thread to make it worth using it
    return configuration.getMinPartSizeBytes() + configuration.getMinPartSizeBytesLowerBound();
  }
}
