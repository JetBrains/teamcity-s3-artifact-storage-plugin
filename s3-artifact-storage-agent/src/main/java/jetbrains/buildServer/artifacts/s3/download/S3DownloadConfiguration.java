package jetbrains.buildServer.artifacts.s3.download;

import com.intellij.openapi.diagnostic.Logger;
import java.nio.file.Path;
import java.util.*;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.artifacts.s3.download.strategy.FileDownloadStrategyType;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.artifacts.s3.S3Constants.*;

public final class S3DownloadConfiguration {
  private static final Logger LOGGER = Logger.getInstance(S3DownloadConfiguration.class);

  // parameter defaults
  private static final boolean DEFAULT_PARALLEL_DOWNLOAD_ENABLED = true;
  private static final boolean DEFAULT_PARALLEL_DOWNLOAD_FORCED = false;
  private static final int DEFAULT_MAX_THREADS = 5;
  private static final int DEFAULT_MIN_PART_SIZE_MB = 100;
  private static final int DEFAULT_MAX_FILE_SIZE_GB = 100;
  private static final int DEFAULT_BUFFER_SIZE_KB = 10;
  private static final int DEFAULT_MAX_CONNECTIONS = 100;
  private static final int DEFAULT_MAX_CONNECTIONS_PER_HOST = 100;

  // parameter bounds
  private static final int LOWER_BOUND_MIN_PART_SIZE_BYTES = 1 * 1024 * 1024; // 1 MB
  private static final int LOWER_BOUND_BUFFER_SIZE_BYTES = 1 * 1024; // 1 KB

  private final long buildId;
  @NotNull
  private final Map<String, String> buildConfigurationParameters;
  @NotNull
  private final Map<String, String> artifactStorageSettings;
  @NotNull
  private final Path buildTempDirectory;

  @NotNull
  private final Map<String, Boolean> memoizedBooleanParameters = new HashMap<>();
  @NotNull
  private final Map<String, Integer> memoizedIntegerParameters = new HashMap<>();

  public S3DownloadConfiguration(@NotNull AgentRunningBuild runningBuild) {
    buildId = runningBuild.getBuildId();
    buildConfigurationParameters = runningBuild.getSharedConfigParameters();
    artifactStorageSettings = runningBuild.getArtifactStorageSettings();
    buildTempDirectory = runningBuild.getBuildTempDirectory().toPath();
  }

  public long getBuildId() {
    return buildId;
  }

  public boolean isParallelDownloadEnabled() {
    return getBooleanParameterOrDefault(S3_PARALLEL_DOWNLOAD_ENABLED, DEFAULT_PARALLEL_DOWNLOAD_ENABLED);
  }

  public boolean isParallelDownloadForced() {
    return getBooleanParameterOrDefault(S3_PARALLEL_DOWNLOAD_FORCED, DEFAULT_PARALLEL_DOWNLOAD_FORCED);
  }

  public int getMaxThreads() {
    return getPositiveIntegerParameterOrDefault(S3_PARALLEL_DOWNLOAD_MAX_THREADS, DEFAULT_MAX_THREADS);
  }

  public long getMinPartSizeBytes() {
    long minPartSizeMB = getPositiveIntegerParameterOrDefault(S3_PARALLEL_DOWNLOAD_MIN_PART_SIZE_MB, DEFAULT_MIN_PART_SIZE_MB);
    return Math.max(getMinPartSizeBytesLowerBound(), minPartSizeMB * 1024 * 1024);
  }

  public long getMaxFileSizeBytes() {
    return (long)getPositiveIntegerParameterOrDefault(S3_PARALLEL_DOWNLOAD_MAX_FILE_SIZE_GB, DEFAULT_MAX_FILE_SIZE_GB) * 1024 * 1024 * 1024;
  }

  public long getMinPartSizeBytesLowerBound() {
    return LOWER_BOUND_MIN_PART_SIZE_BYTES;
  }

  public long getParallelDownloadFileSizeThreshold() {
    // there should be at least the lower bound for minimum part size bytes left for the second thread to make it worth using it
    return getMinPartSizeBytes() + getMinPartSizeBytesLowerBound();
  }

  public boolean isS3CompatibleStorage() {
    String storageType = artifactStorageSettings.get(TEAMCITY_STORAGE_TYPE_KEY);
    return StringUtil.areEqual(storageType, S3_STORAGE_TYPE) || StringUtil.areEqual(storageType, S3_COMPATIBLE_STORAGE_TYPE);
  }

  // todo split into two separate buffers for network and disk IO?
  public int getBufferSizeBytes() {
    int bufferSizeKB = getPositiveIntegerParameterOrDefault(S3_PARALLEL_DOWNLOAD_BUFFER_SIZE_KB, DEFAULT_BUFFER_SIZE_KB);
    return Math.min(LOWER_BOUND_BUFFER_SIZE_BYTES, bufferSizeKB * 1024);
  }

  @Nullable
  public FileDownloadStrategyType getForcedDownloadStrategyType() {
    return Arrays.stream(FileDownloadStrategyType.values())
                 .filter(strategy -> strategy.name().equals(buildConfigurationParameters.get(S3_PARALLEL_DOWNLOAD_FORCED_STRATEGY)))
                 .findAny().orElse(null);
  }

  public int getMaxConnectionsPerHost() {
    return getPositiveIntegerParameterOrDefault(S3_PARALLEL_DOWNLOAD_MAX_CONNECTIONS_PER_HOST, DEFAULT_MAX_CONNECTIONS_PER_HOST);
  }

  public int getMaxConnectionsTotal() {
    return getPositiveIntegerParameterOrDefault(S3_PARALLEL_DOWNLOAD_MAX_CONNECTIONS, DEFAULT_MAX_CONNECTIONS);
  }

  @NotNull
  public Path getBuildTempDirectory() {
    return buildTempDirectory;
  }

  private boolean getBooleanParameterOrDefault(@NotNull String paramName, boolean defaultValue) {
    return memoizedBooleanParameters.computeIfAbsent(paramName, name -> {
      return Optional.ofNullable(buildConfigurationParameters.get(paramName))
                     .map(Boolean::parseBoolean)
                     .orElse(defaultValue);
    });
  }

  private int getPositiveIntegerParameterOrDefault(@NotNull String paramName, int defaultValue) {
    return memoizedIntegerParameters.computeIfAbsent(paramName, name -> {
      return Optional.ofNullable(buildConfigurationParameters.get(paramName))
                     .map(intString -> safeParsePositiveInteger(intString, paramName))
                     .orElse(defaultValue);
    });
  }

  @Nullable
  private Integer safeParsePositiveInteger(@NotNull String intString, @NotNull String paramName) {
    try {
      int parsedInt = Integer.parseInt(intString);
      if (parsedInt > 0) return parsedInt;
      LOGGER.warn(String.format("Configuration parameter %s with non-positive value %s will be ignored", paramName, intString));
    } catch (NumberFormatException e) {
      LOGGER.warn(String.format("Failed to parse integer configuration parameter %s from value %s: %s", paramName, intString, e.getMessage()));
    }
    return null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    S3DownloadConfiguration that = (S3DownloadConfiguration)o;
    return buildId == that.buildId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(buildId);
  }
}
