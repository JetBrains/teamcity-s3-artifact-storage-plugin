package jetbrains.buildServer.artifacts.s3.publish;

import com.intellij.openapi.util.Pair;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Upload statistics for specific object
 * Provides information about upload start time, upload duration, list of upload errors and whether upload has been successful in the end
 */
public class UploadStatistics {
  private static class PartUploadStatistics {
    private Instant startTime;
    private Instant endTime;
    private boolean isSuccesfull = true;

    private PartUploadStatistics(Instant startTime) {
      this.startTime = startTime;
    }

    private PartUploadStatistics restart(Instant startTime) {
      this.startTime = startTime;
      return this;
    }

    private PartUploadStatistics finish(Instant endTime) {
      this.endTime = endTime;
      isSuccesfull = true;
      return this;
    }

    private PartUploadStatistics fail(Instant failTime) {
      endTime = failTime;
      isSuccesfull = false;
      return this;
    }
  }

  private static class PartStatisticsAggregation {
    private final Duration duration;
    private final boolean isSuccesfull;

    private PartStatisticsAggregation(Duration duration, boolean isSuccesfull) {
      this.duration = duration;
      this.isSuccesfull = isSuccesfull;
    }
  }

  private final Map<Number, PartUploadStatistics> partsStatistics = new ConcurrentHashMap<>();
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

  public void setPartStartTime(int partIndex, @NotNull Instant startTime) {
    partsStatistics.compute(partIndex, (index, partStat) -> {
      if (partStat != null) {
        return partStat.restart(startTime);
      }
      return new PartUploadStatistics(startTime);
    });
  }

  public UploadStatistics finish(Instant endTime) {
    if (!partsStatistics.isEmpty()) {
      PartStatisticsAggregation calclulatedStat = calclulatePartsStatistics();
      if (calclulatedStat.duration != null) {
        myDuration = calclulatedStat.duration;
        mySuccessful = calclulatedStat.isSuccesfull;
        return this;
      }
    }
    if (myStartTime != null) {
      mySuccessful = true;
      myDuration = myDuration.plus(Duration.between(myStartTime, endTime));
      myStartTime = null;
    }
    return this;
  }

  public UploadStatistics partFinish(int partIndex, Instant endTime) {
    partsStatistics.computeIfPresent(partIndex, (index, partStat) -> partStat.finish(endTime));
    return this;
  }

  public UploadStatistics fail(Instant failTime, String error) {
    if (!partsStatistics.isEmpty()) {
      PartStatisticsAggregation calclulatedStat = calclulatePartsStatistics();
      if (calclulatedStat.duration != null) {
        myDuration = calclulatedStat.duration;
        mySuccessful = false;
        myErrors.add(error);
        return this;
      }
    }
    if (myStartTime != null) {
      mySuccessful = false;
      myDuration = myDuration.plus(Duration.between(myStartTime, failTime));
      myErrors.add(error);
      myStartTime = null;
    }
    return this;
  }

  public UploadStatistics partFail(int partIndex, Instant failTime, String error) {
    partsStatistics.computeIfPresent(partIndex, (index, partStat) -> partStat.fail(failTime));
    myErrors.add(error);
    return this;
  }

  public UploadStatistics addAditionalTiming(String timingKey, Duration duration) {
    myAdditionalTimings.put(timingKey, duration);
    return this;
  }

  private PartStatisticsAggregation calclulatePartsStatistics() {
    Instant start = Instant.ofEpochMilli(Long.MAX_VALUE);
    Instant finish = Instant.ofEpochMilli(Long.MIN_VALUE);
    boolean isSuccessful = true;
    for (PartUploadStatistics partStat: partsStatistics.values()) {
      if (partStat.startTime != null && partStat.startTime.isBefore(start)) {
        start = partStat.startTime;
      }
      if (partStat.endTime != null && partStat.endTime.isAfter(finish)) {
        finish = partStat.endTime;
      }
      isSuccessful = isSuccessful && partStat.isSuccesfull;
    }
    return new PartStatisticsAggregation(start.isBefore(finish) ? Duration.between(start, finish) : null, isSuccessful);
  }
}
