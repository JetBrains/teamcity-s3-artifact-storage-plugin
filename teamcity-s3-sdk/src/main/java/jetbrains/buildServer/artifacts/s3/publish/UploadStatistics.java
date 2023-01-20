package jetbrains.buildServer.artifacts.s3.publish;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Upload statistics for specific object
 * Provides information about upload start time, upload duration, list of upload errors and whether upload has been successful in the end
 */
public class UploadStatistics {
  @NotNull
  private final String myObjectKey;
  @Nullable
  private Instant myStartTime;
  @NotNull
  private final Instant myInitialStartTime;

  @NotNull
  private Duration myDuration = Duration.ZERO;
  @NotNull
  private final List<String> myErrors = new ArrayList<>();

  private boolean mySuccessful;
  @NotNull
  private final Map<String, Duration> myAdditionalTimings = new HashMap<>();

  public UploadStatistics(@NotNull String objectKey, @NotNull Instant startTime) {
    myObjectKey = objectKey;
    myStartTime = startTime;
    myInitialStartTime = startTime;
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

  public boolean isSuccessful() {
    return mySuccessful;
  }

  @NotNull
  public Instant getInitialStartTime() {
    return myInitialStartTime;
  }

  @NotNull
  public Map<String, Duration> getAdditionalTimings() {
    return Collections.unmodifiableMap(myAdditionalTimings);
  }

  public void setStartTime(@NotNull Instant startTime) {
    myStartTime = startTime;
  }

  public UploadStatistics finish(Instant endTime) {
    if (myStartTime != null) {
      mySuccessful = true;
      myDuration = myDuration.plus(Duration.between(myStartTime, endTime));
      myStartTime = null;
    }
    return this;
  }

  public UploadStatistics fail(Instant failTime, String error) {
    if (myStartTime != null) {
      mySuccessful = false;
      myDuration = myDuration.plus(Duration.between(myStartTime, failTime));
      myErrors.add(error);
      myStartTime = null;
    }
    return this;
  }

  public UploadStatistics addAditionalTiming(String timingKey, Duration duration) {
    myAdditionalTimings.put(timingKey, duration);
    return this;
  }
}
