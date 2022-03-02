package jetbrains.buildServer.artifacts.s3.publish;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Upload statistics for specific object
 * Provides information about upload start time, upload duration, list of upload errors and whether upload has been successful in the end
 */
public class UploadStatistics {
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
  public UploadStatistics merge(@Nullable UploadStatistics other) {
    if (other == null) {
      return this;
    }
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
