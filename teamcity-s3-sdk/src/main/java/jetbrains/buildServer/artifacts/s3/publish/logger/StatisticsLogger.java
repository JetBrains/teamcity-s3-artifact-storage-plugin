package jetbrains.buildServer.artifacts.s3.publish.logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jetbrains.buildServer.artifacts.s3.publish.UploadStatistics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * Helper class that logs statistical data for S3 publishing.
 * Logs start times, durations, errors and whether upload ended successfully
 * Can log total duration of upload process with multiple retries
 * Can provide summarized statistics for all uploads with total and average duration
 */
public class StatisticsLogger {

  @NotNull
  private final Map<String, UploadStatistics> myStatisticsMap = new ConcurrentHashMap<>();

  /**
   * Record start of the upload
   *
   * @param objectKey - key of the object that is being uploaded
   * @param startTime - starting time of the upload
   * @param fileSize - object file size
   */
  public void uploadStarted(@NotNull String objectKey, @NotNull Instant startTime, long fileSize) {
    myStatisticsMap.compute(objectKey, (key, stat) -> {
      if (stat != null) {
        stat.setStartTime(startTime);
        stat.setFileSize(fileSize);
        return stat;
      } else {
        return new UploadStatistics(objectKey, startTime, fileSize);
      }
    });
  }

  /**
   * Record start of the upload of the object part
   *
   * @param objectKey - key of the object that is being uploaded
   * @param startTime - starting time of the upload
   * @param partIndex - index of the uploaded object part
   * @param partSize - size of the uploaded object part
   */
  public void partUploadStarted(@NotNull String objectKey, @NotNull Instant startTime, int partIndex, long partSize) {
    myStatisticsMap.compute(objectKey, (key, stat) -> {
      if (stat == null) {
        stat = new UploadStatistics(objectKey, startTime, partSize);
      }
      stat.setPartStartTime(partIndex, startTime);
      return stat;
    });
  }

  /**
   * Record end of a successful upload and persist upload statistics
   *
   * @param objectKey - key of the object that is being uploaded
   * @param endTime   - time when upload was finished
   * @param digest    - checksum of the uploaded object
   */
  public void uploadFinished(@NotNull String objectKey, @NotNull Instant endTime, String digest) {
    myStatisticsMap.computeIfPresent(objectKey, (key, stat) -> {
      stat.setChecksum(digest);
      stat.finish(endTime);
      return stat;
    });
  }

  /**
   * Record end of a successful upload of the object part and persist upload statistics
   *
   * @param objectKey - key of the object that is being uploaded
   * @param endTime   - time when upload was finished
   * @param partIndex - index of the uploaded object part
   */
  public void partUploadFinished(@NotNull String objectKey, @NotNull Instant endTime, int partIndex) {
    myStatisticsMap.computeIfPresent(objectKey, (key, stat) -> stat.partFinish(partIndex, endTime));
  }

  /**
   * Record end of the upload that failed and persist upload statistics
   *
   * @param objectKey - key of the object that is being uploaded
   * @param error     - Text of an error
   * @param errorTime - time when upload failed
   */
  public void uploadFailed(@NotNull String objectKey, @NotNull String error, @NotNull Instant errorTime) {
    myStatisticsMap.computeIfPresent(objectKey, (key, stat) -> stat.fail(errorTime, error));
  }

  /**
   * Record end of the upload that failed and persist upload statistics
   *
   * @param objectKey - key of the object that is being uploaded
   * @param error     - Text of an error
   * @param errorTime - time when upload failed
   * @param partIndex - index of the uploaded object part
   */
  public void partUploadFailed(@NotNull String objectKey, @NotNull String error, @NotNull Instant errorTime, int partIndex) {
    myStatisticsMap.computeIfPresent(objectKey, (key, stat) -> stat.partFail(partIndex, errorTime, error));
  }

  /**
   * Returns statistics for specific object
   *
   * @param objectKey - key of the object that is being uploaded
   * @return statistics for specified object key
   */
  @Nullable
  public UploadStatistics getObjectStatistics(@NotNull String objectKey) {
    return myStatisticsMap.get(objectKey);
  }

  /**
   * Returns all statistic records that are currently persisted
   *
   * @return collection of statistics records
   */
  @NotNull
  public Collection<UploadStatistics> getAllRecords() {
    return myStatisticsMap.values();
  }

  /**
   * Returns number of currently persisted statistic records
   *
   * @return number of records
   */
  public int size() {
    return myStatisticsMap.size();
  }

  public void partsSeparated(@NotNull String objectKey, Duration duration, int nParts, long chunkSizeInBytes) {
    myStatisticsMap.computeIfPresent(objectKey, (key, stat) -> {
      stat.addAditionalTiming("Dividing into chunks", duration);
      stat.setNumberOfParts(nParts);
      stat.setChunkSize(chunkSizeInBytes);
      return stat;
    });
  }

  public void urlsGenerated(String objectKey, Duration duration) {
    myStatisticsMap.computeIfPresent(objectKey, (key, stat) -> stat.addAditionalTiming("Generating URL(s)", duration));
  }


  /**
   * Summarized statistics from multiple uploads for different files
   * Can provide information about total duration of upload process, average duration of single file upload in the batch and also list all errors
   */
  public static class SummaryStatistics {
    @NotNull
    private final Duration totalDuration;
    @NotNull
    private final Duration averageDuration;

    private final int fileCount;
    @NotNull
    private final List<String> errors;

    public SummaryStatistics(@NotNull Duration totalDuration, @NotNull Duration averageDuration, int fileCount, @NotNull List<String> errors) {
      this.totalDuration = totalDuration;
      this.averageDuration = averageDuration;
      this.fileCount = fileCount;
      this.errors = errors;
    }

    @NotNull
    public Duration getTotalDuration() {
      return totalDuration;
    }

    @NotNull
    public Duration getAverageDuration() {
      return averageDuration;
    }

    public int getFileCount() {
      return fileCount;
    }

    @NotNull
    public List<String> getErrors() {
      return errors;
    }
  }
}
