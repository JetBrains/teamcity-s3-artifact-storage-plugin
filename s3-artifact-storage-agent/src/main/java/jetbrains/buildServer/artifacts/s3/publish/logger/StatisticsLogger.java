package jetbrains.buildServer.artifacts.s3.publish.logger;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
   * Calculates summarized statistics for all records that are currently persisted
   *
   * @return summarized statistics for all records
   */
  @NotNull
  public SummaryStatistics getSummaryStatistics() {
    Duration totalDuration = Duration.ofMillis(0);
    int fileCount = 0;
    List<String> errors = new ArrayList<>();
    for (UploadStatistics stat : myStatisticsMap.values()) {
      totalDuration = totalDuration.plus(stat.getDuration());
      fileCount++;
      errors.addAll(stat.getErrors());
    }
    final Duration averageDuration = totalDuration.dividedBy(fileCount);
    return new SummaryStatistics(totalDuration, averageDuration, fileCount, errors);
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
    final UploadStatistics oldStatistics = myStatisticsMap.get(objectKey);
    if (oldStatistics != null) {
      statistics = statistics.merge(oldStatistics);
    }

    myStatisticsMap.put(objectKey, statistics);
  }

  /**
   * Upload statistics for specific object
   * Provides information about upload start time, upload duration, list of upload errors and whether upload has been successful in the end
   */
  public static class UploadStatistics {
    @NotNull
    private final String myObjectKey;
    @NotNull
    private final Instant myStartTime;
    @NotNull
    private final Duration myDuration;
    @NotNull
    private final List<String> myErrors;

    private final boolean mySuccessful;

    public UploadStatistics(@NotNull String objectKey, @NotNull Instant startTime,
                            @NotNull Duration duration) {
      this(objectKey, startTime, duration, new ArrayList<>(), true);
    }

    public UploadStatistics(@NotNull String objectKey, @NotNull Instant startTime, @NotNull Duration duration, @NotNull List<String> errors) {
      this(objectKey, startTime, duration, errors, false);
    }

    public UploadStatistics(@NotNull String objectKey, @NotNull Instant startTime, @NotNull Duration duration, @NotNull List<String> errors, boolean successful) {
      myObjectKey = objectKey;
      myStartTime = startTime;
      myDuration = duration;
      myErrors = errors;
      mySuccessful = successful;
    }

    @NotNull
    public String getObjectKey() {
      return myObjectKey;
    }

    @NotNull
    public Duration getDuration() {
      return myDuration;
    }

    @NotNull
    public List<String> getErrors() {
      return myErrors;
    }

    public boolean hasErrors() {
      return !myErrors.isEmpty();
    }

    /**
     * Merges this statistics with statistics for another upload attempt for the same object
     * keeps earliest start time, summarizes duration, and gathers all the errors
     *
     * @param other - statistics for other upload attempt
     * @return - new statistics object that has information about both attempts
     */
    @NotNull
    public UploadStatistics merge(@NotNull UploadStatistics other) {
      if (!myObjectKey.equals(other.myObjectKey)) {
        throw new IllegalArgumentException("Trying to merge statistics for different objects");
      }
      Duration duration = myDuration.plus(other.myDuration);

      Instant startTime = myStartTime.isBefore(other.myStartTime) ? myStartTime : other.myStartTime;

      final ArrayList<String> errors = new ArrayList<>();
      errors.addAll(myErrors);
      errors.addAll(other.myErrors);

      boolean success = isSuccessful() || other.isSuccessful();
      return new UploadStatistics(myObjectKey, startTime, duration, errors, success);
    }

    public boolean isSuccessful() {
      return mySuccessful;
    }
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
