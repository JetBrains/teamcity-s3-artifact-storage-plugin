package jetbrains.buildServer.artifacts.s3.download;

import com.intellij.openapi.diagnostic.Logger;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.artifacts.s3.download.strategy.FileDownloadStrategyType;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.artifacts.s3.S3Constants.*;

public final class S3DownloadConfiguration {
  private static final Logger LOGGER = Logger.getInstance(S3DownloadConfiguration.class);
  private static final boolean DEFAULT_PARALLEL_DOWNLOAD_ENABLED = false;
  private static final boolean DEFAULT_PARALLEL_DOWNLOAD_FORCED = false;
  private static final int DEFAULT_MAX_THREADS = 10;
  private static final int DEFAULT_PART_SIZE_MB = 100;
  private static final int DEFAULT_BUFFER_SIZE_KB = 10;
  private static final int DEFAULT_MAX_CONNECTIONS = 100;
  private static final int DEFAULT_MAX_CONNECTIONS_PER_HOST = 100;
  private static final int MIN_PART_SIZE_BYTES = 1 * 1024 * 1024; // 1 MB
  private static final int MIN_BUFFER_SIZE_BYTES = 1 * 1024; // 1 KB

  private final long buildId;
  @NotNull
  private final Map<String, String> buildConfigurationParameters;
  @NotNull
  private final Map<String, String> artifactStorageSettings;
  @NotNull
  private final Path buildTempDirectory;

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

  public long getPartSizeBytes() {
    long partSizeMB = getPositiveIntegerParameterOrDefault(S3_PARALLEL_DOWNLOAD_PART_SIZE_MB, DEFAULT_PART_SIZE_MB);
    return Math.max(getMinPartSizeBytes(), partSizeMB * 1024 * 1024);
  }

  public long getMinPartSizeBytes() {
    return MIN_PART_SIZE_BYTES;
  }

  public long getParallelDownloadFileSizeThreshold() {
    return getPartSizeBytes() + getMinPartSizeBytes(); // there should be at least minimum part size bytes left for the second thread to make it worth using it
  }

  public boolean isS3CompatibleStorage() {
    String storageType = artifactStorageSettings.get(TEAMCITY_STORAGE_TYPE_KEY);
    return StringUtil.areEqual(storageType, S3_STORAGE_TYPE) || StringUtil.areEqual(storageType, S3_COMPATIBLE_STORAGE_TYPE);
  }

  // todo split into two separate buffers for network and disk IO?
  public int getBufferSizeBytes() {
    int bufferSizeKB = getPositiveIntegerParameterOrDefault(S3_PARALLEL_DOWNLOAD_BUFFER_SIZE_KB, DEFAULT_BUFFER_SIZE_KB);
    return Math.min(MIN_BUFFER_SIZE_BYTES, bufferSizeKB * 1024);
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
    return Optional.ofNullable(buildConfigurationParameters.get(paramName))
                   .map(Boolean::parseBoolean)
                   .orElse(defaultValue);
  }

  private int getPositiveIntegerParameterOrDefault(String paramName, int defaultValue) {
    return Optional.ofNullable(buildConfigurationParameters.get(paramName))
                   .map(intString -> safeParsePositiveInteger(intString, paramName))
                   .orElse(defaultValue);
  }

  @Nullable
  private Integer safeParsePositiveInteger(@NotNull String intString, @NotNull String paramName) {
    try {
      int parsedInt = Integer.parseInt(intString);
      if (parsedInt > 0) return parsedInt;
      LOGGER.warn(String.format("Configuration parameter %s with non-positive value %s will be ignored", paramName, intString));
    } catch (NumberFormatException e) {
      LOGGER.warnAndDebugDetails(String.format("Failed to parse integer configuration parameter %s from value %s", paramName, intString), e);
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
