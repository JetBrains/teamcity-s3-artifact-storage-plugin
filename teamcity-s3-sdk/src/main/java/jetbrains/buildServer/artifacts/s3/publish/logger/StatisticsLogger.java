package jetbrains.buildServer.artifacts.s3.publish.logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
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
  private final Map<String, Instant> myStartTimesMap = new ConcurrentHashMap<>();
  @NotNull
  private final Map<String, UploadStatistics> myStatisticsMap = new ConcurrentHashMap<>();

  /**
   * Record start of the upload
   *
   * @param objectKey - key of the object that is being uploaded
   * @param startTime - starting time of the upload
   */
  public void uploadStarted(@NotNull String objectKey, @NotNull Instant startTime) {
    myStartTimesMap.putIfAbsent(objectKey, startTime);
  }

  /**
   * Record end of a successful upload and persist upload statistics
   *
   * @param objectKey - key of the object that is being uploaded
   * @param endTime   - time when upload was finished
   */
  public void uploadFinished(@NotNull String objectKey, @NotNull Instant endTime) {
    final Instant startTime = myStartTimesMap.get(objectKey);
    if (startTime != null) {
      myStartTimesMap.remove(objectKey);
      final Duration duration = Duration.between(startTime, endTime);
      putOrMerge(objectKey, new UploadStatistics(objectKey, startTime, duration));
    }
  }


  /**
   * Record end of the upload that failed and persist upload statistics
   *
   * @param objectKey - key of the object that is being uploaded
   * @param error     - Text of an error
   * @param errorTime - time when upload failed
   */
  public void uploadFailed(@NotNull String objectKey, @NotNull String error, @NotNull Instant errorTime) {
    final Instant startTime = myStartTimesMap.get(objectKey);
    if (startTime != null) {
      myStartTimesMap.remove(objectKey);
      UploadStatistics statistics = new UploadStatistics(objectKey, startTime, Duration.between(startTime, errorTime), Collections.singletonList(error));

      putOrMerge(objectKey, statistics);
    }
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

  private void putOrMerge(String objectKey, UploadStatistics statistics) {
    myStatisticsMap.compute(objectKey, (key, value) -> statistics.merge(value));
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
