package jetbrains.buildServer.artifacts.s3.download.parallel.splitter.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jetbrains.buildServer.artifacts.s3.download.S3DownloadConfiguration;
import jetbrains.buildServer.artifacts.s3.download.parallel.FilePart;
import jetbrains.buildServer.artifacts.s3.download.parallel.splitter.FileSplitter;
import jetbrains.buildServer.artifacts.s3.download.parallel.splitter.SplitabilityReport;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_PARALLEL_DOWNLOAD_MAX_THREADS;
import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_PARALLEL_DOWNLOAD_MIN_PART_SIZE_MB;

public final class FileSplitterImpl implements FileSplitter {
  @NotNull
  private final S3DownloadConfiguration myConfiguration;

  public FileSplitterImpl(@NotNull S3DownloadConfiguration configuration) {
    myConfiguration = configuration;
  }

  @NotNull
  public List<FilePart> split(long fileSize) {
    if (fileSize <= 0) throw new IllegalArgumentException("File size is not positive: " + fileSize);

    final int maxThreads = myConfiguration.getMaxThreads();
    if (fileSize < getSplitThreshold() || maxThreads == 1) {
      // unsplittable, return one part
      return Collections.singletonList(new FilePart(0, 0, fileSize - 1));
    }

    List<FilePart> parts = new ArrayList<>();
    final long minPartSize = myConfiguration.getMinPartSizeBytes();
    final long partSize;
    if (fileSize < maxThreads * minPartSize) {
      // from 2 to maxThreads parts of size = minPartSize except, possibly, the last
      partSize = minPartSize;

      long residualSize = fileSize % partSize;
      int fullParts = (int)(fileSize / partSize); // fileSize / partSize < maxThreads, safe to case to int
      boolean extendLastFullPart = residualSize < myConfiguration.getMinPartSizeLowerBoundBytes(); // don't create new part for too few bytes, extend the last full part

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

  @NotNull
  @Override
  public SplitabilityReport testSplitability(long fileSize) {
    long splitThreshold = getSplitThreshold();
    if (fileSize < splitThreshold) {
      return SplitabilityReport.unsplittable(String.format("File size %s is less than split threshold %s = minimum part size %s (can be configured by %s) + leftower limit %s",
                                                           fileSize, splitThreshold, myConfiguration.getMinPartSizeBytes(), S3_PARALLEL_DOWNLOAD_MIN_PART_SIZE_MB,
                                                           myConfiguration.getMinPartSizeLowerBoundBytes()));
    }

    if (myConfiguration.getMaxThreads() == 1) {
      return SplitabilityReport.unsplittable(String.format("Maximum number of threads is 1 (can be configured by %s)", S3_PARALLEL_DOWNLOAD_MAX_THREADS));
    }

    return SplitabilityReport.splittable();
  }

  private long getSplitThreshold() {
    // there should be at least the lower bound for minimum part size left for the second thread to make it worth using it
    return myConfiguration.getMinPartSizeBytes() + myConfiguration.getMinPartSizeLowerBoundBytes();
  }
}
