package jetbrains.buildServer.artifacts.s3.download;

import com.intellij.openapi.diagnostic.Logger;
import java.util.*;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.artifacts.s3.download.parallel.strategy.impl.InplaceParallelDownloadStrategy;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.artifacts.s3.S3Constants.*;
import static jetbrains.buildServer.artifacts.s3.download.S3DownloadConfiguration.IntegerParameterBounds.*;

public class S3DownloadConfiguration {
  private static final Logger LOGGER = Logger.getInstance(S3DownloadConfiguration.class);

  // parameter defaults
  private static final boolean DEFAULT_PARALLEL_DOWNLOAD_ENABLED = false; // todo invert before the release
  private static final boolean DEFAULT_PARALLEL_DOWNLOAD_FORCED = false;
  private static final int DEFAULT_MAX_THREADS = 5;
  private static final int DEFAULT_MIN_PART_SIZE_MB = 100;
  private static final int DEFAULT_MAX_FILE_SIZE_GB = 1024;
  private static final int DEFAULT_BUFFER_SIZE_KB = 10;
  private static final int DEFAULT_MAX_CONNECTIONS = 100;
  private static final int DEFAULT_MAX_CONNECTIONS_PER_HOST = 100;
  private static final String DEFAULT_PARALLEL_STRATEGY = InplaceParallelDownloadStrategy.NAME;

  // int parameter bounds: some sane numbers for lower and upper bounds
  private static final IntegerParameterBounds BOUNDS_MAX_THREADS = lowerAndUpper(1, 1000);
  private static final IntegerParameterBounds BOUNDS_MIN_PART_SIZE_MB = lower(1);
  private static final IntegerParameterBounds BOUNDS_MAX_FILE_SIZE_GB = unbound();
  private static final IntegerParameterBounds BOUNDS_BUFFER_SIZE_KB = lowerAndUpper(1, 1024 * 1024);
  private static final IntegerParameterBounds BOUNDS_MAX_CONNECTIONS = lowerAndUpper(1, 100_000);
  private static final IntegerParameterBounds BOUNDS_MAX_CONNECTIONS_PER_HOST = lowerAndUpper(1, 100_000);

  private final long myBuildId;
  @NotNull
  private final Map<String, String> myBuildConfigurationParameters;
  @NotNull
  private final Map<String, String> myArtifactStorageSettings;

  @NotNull
  private final Map<String, Boolean> myMemoizedBooleanParameters = new HashMap<>();
  @NotNull
  private final Map<String, Integer> myMemoizedIntegerParameters = new HashMap<>();

  public S3DownloadConfiguration(@NotNull AgentRunningBuild runningBuild) {
    myBuildId = runningBuild.getBuildId();
    myBuildConfigurationParameters = runningBuild.getSharedConfigParameters();
    myArtifactStorageSettings = runningBuild.getArtifactStorageSettings();
  }

  public long getBuildId() {
    return myBuildId;
  }

  public boolean isParallelDownloadEnabled() {
    return getBooleanParameterOrDefault(S3_PARALLEL_DOWNLOAD_ENABLED, DEFAULT_PARALLEL_DOWNLOAD_ENABLED);
  }

  public boolean isParallelDownloadForced() {
    return getBooleanParameterOrDefault(S3_PARALLEL_DOWNLOAD_FORCED, DEFAULT_PARALLEL_DOWNLOAD_FORCED);
  }

  public int getMaxThreads() {
    return getBoundIntegerParameterOrDefault(S3_PARALLEL_DOWNLOAD_MAX_THREADS, DEFAULT_MAX_THREADS, BOUNDS_MAX_THREADS);
  }

  public long getMinPartSizeBytes() {
    return (long)getBoundIntegerParameterOrDefault(S3_PARALLEL_DOWNLOAD_MIN_PART_SIZE_MB, DEFAULT_MIN_PART_SIZE_MB, BOUNDS_MIN_PART_SIZE_MB) * 1024 * 1024;
  }

  public long getMaxFileSizeBytes() {
    return (long)getBoundIntegerParameterOrDefault(S3_PARALLEL_DOWNLOAD_MAX_FILE_SIZE_GB, DEFAULT_MAX_FILE_SIZE_GB, BOUNDS_MAX_FILE_SIZE_GB) * 1024 * 1024 * 1024;
  }

  public long getMinPartSizeLowerBoundBytes() {
    return (long)BOUNDS_MIN_PART_SIZE_MB.lower * 1024 * 1024;
  }

  public int getBufferSizeBytes() {
    return getBoundIntegerParameterOrDefault(S3_PARALLEL_DOWNLOAD_BUFFER_SIZE_KB, DEFAULT_BUFFER_SIZE_KB, BOUNDS_BUFFER_SIZE_KB) * 1024;
  }

  public int getMaxConnectionsPerHost() {
    return getBoundIntegerParameterOrDefault(S3_PARALLEL_DOWNLOAD_MAX_CONNECTIONS_PER_HOST, DEFAULT_MAX_CONNECTIONS_PER_HOST, BOUNDS_MAX_CONNECTIONS_PER_HOST);
  }

  public int getMaxConnectionsTotal() {
    return getBoundIntegerParameterOrDefault(S3_PARALLEL_DOWNLOAD_MAX_CONNECTIONS, DEFAULT_MAX_CONNECTIONS, BOUNDS_MAX_CONNECTIONS);
  }

  public boolean isS3CompatibleStorage() {
    String storageType = myArtifactStorageSettings.get(TEAMCITY_STORAGE_TYPE_KEY);
    return StringUtil.areEqual(storageType, S3_STORAGE_TYPE) || StringUtil.areEqual(storageType, S3_COMPATIBLE_STORAGE_TYPE);
  }

  @NotNull
  public String getParallelStrategyName() {
    return Optional.ofNullable(myBuildConfigurationParameters.get(S3_PARALLEL_DOWNLOAD_STRATEGY))
      .orElse(DEFAULT_PARALLEL_STRATEGY);
  }

  private boolean getBooleanParameterOrDefault(@NotNull String paramName, boolean defaultValue) {
    return myMemoizedBooleanParameters.computeIfAbsent(paramName, name -> {
      return Optional.ofNullable(myBuildConfigurationParameters.get(paramName))
        .map(Boolean::parseBoolean)
        .orElse(defaultValue);
    });
  }

  private int getBoundIntegerParameterOrDefault(@NotNull String paramName, int defaultValue, @NotNull IntegerParameterBounds bounds) {
    return myMemoizedIntegerParameters.computeIfAbsent(paramName, name -> {
      return Optional.ofNullable(myBuildConfigurationParameters.get(paramName))
        .map(stringValue -> safeParseInteger(stringValue, paramName, defaultValue))
        .map(value -> applyBounds(value, paramName, defaultValue, bounds))
        .orElse(defaultValue);
    });
  }

  @NotNull
  private static Integer safeParseInteger(@NotNull String stringValue, @NotNull String paramName, int defaultValue) {
    try {
      return Integer.parseInt(stringValue);
    } catch (NumberFormatException e) {
      LOGGER.warn(
        String.format("Failed to parse integer configuration parameter %s from value %s: %s; using the default value %s", paramName, stringValue, e.getMessage(), defaultValue));
      return defaultValue;
    }
  }

  @NotNull
  private static Integer applyBounds(@NotNull Integer value, @NotNull String paramName, int defaultValue, @NotNull IntegerParameterBounds bounds) {
    if (value < bounds.lower || value > bounds.upper) {
      LOGGER.warn(String.format("Value %s of configuration parameter %s is out of bounds %s, using the default value %s", value, paramName, bounds, defaultValue));
      return defaultValue;
    }
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    S3DownloadConfiguration that = (S3DownloadConfiguration)o;
    return myBuildId == that.myBuildId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(myBuildId);
  }

  static final class IntegerParameterBounds {
    private final int lower;
    private final int upper;

    private IntegerParameterBounds(int lower, int upper) {
      this.lower = lower;
      this.upper = upper;
    }

    public static IntegerParameterBounds lower(int lower) {
      return new IntegerParameterBounds(lower, Integer.MAX_VALUE);
    }

    public static IntegerParameterBounds upper(int upper) {
      return new IntegerParameterBounds(Integer.MIN_VALUE, upper);
    }

    public static IntegerParameterBounds lowerAndUpper(int lower, int upper) {
      return new IntegerParameterBounds(lower, upper);
    }

    public static IntegerParameterBounds unbound() {
      return new IntegerParameterBounds(Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    @Override
    public String toString() {
      return "[" + lower + ", " + upper + "]";
    }
  }
}
