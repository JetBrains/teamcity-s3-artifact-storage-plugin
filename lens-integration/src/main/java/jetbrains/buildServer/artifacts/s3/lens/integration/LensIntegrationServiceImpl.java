package jetbrains.buildServer.artifacts.s3.lens.integration;

import java.time.Duration;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.lens.integration.dto.UploadFileEvent;
import jetbrains.buildServer.artifacts.s3.lens.integration.dto.UploadInfoEvent;
import jetbrains.buildServer.artifacts.s3.publish.UploadStatistics;
import jetbrains.buildServer.artifacts.s3.publish.presigned.upload.TeamCityConnectionConfiguration;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.util.StringUtil;
import org.apache.commons.codec.digest.DigestUtils;
import org.jetbrains.annotations.NotNull;

public class LensIntegrationServiceImpl implements LensIntegrationService {

  private boolean isLensPluginInstalled(LowLevelLensClient lensClient) {
    return lensClient.probeLensConnection();
  }

  @Override
  public void generateUploadEvents(@NotNull final AgentRunningBuild build,
                                   @NotNull final Collection<UploadStatistics> statistics,
                                   @NotNull final Duration totalUploadDuration,
                                   @NotNull final TeamCityConnectionConfiguration teamCityConnectionConfiguration) {
    // if lens plugin is unavailable, ignore the call
    LowLevelLensClient lensClient = new LowLevelLensClient(teamCityConnectionConfiguration);
    if (!isLensPluginInstalled(lensClient)) {
      Loggers.AGENT.debug("Lens plugin is not installed, ignoring the call to generate upload events");
      return;
    }

    long buildId = build.getBuildId();
    UploadInfoEvent uploadInfoEvent = buildUploadInfoEvent(statistics, totalUploadDuration);

    try {
      // send complete event
      if (isLensUploadInfoEventsEnabled(build)) {
        lensClient.publishUploadInfoEvent(buildId, uploadInfoEvent).get();
      }
      // send inidivitual events
      if (isLensUploadFileEventsEnabled(build)) {
        CompletableFuture[] futures = sendFileEvents(statistics, lensClient, buildId);
        CompletableFuture.allOf(futures).get();
      }
    } catch (Exception e) {
      Loggers.AGENT.warnAndDebugDetails(
        String.format(
          "Failed to publish some of the events to the lens: buildId=%d",
          buildId), e);
    }
  }

  private boolean isLensUploadFileEventsEnabled(AgentRunningBuild build) {
    return Boolean.parseBoolean(build.getSharedConfigParameters().get(S3Constants.S3_ENABLE_UPLOAD_FILE_LENS_EVENTS));
  }

  private boolean isLensUploadInfoEventsEnabled(AgentRunningBuild build) {
    return Boolean.parseBoolean(build.getSharedConfigParameters().get(S3Constants.S3_ENABLE_UPLOAD_INFO_LENS_EVENTS));
  }

  private static CompletableFuture[] sendFileEvents(@NotNull Collection<UploadStatistics> statistics, @NotNull LowLevelLensClient lensClient, long buildId) {
    return statistics.stream()
                     // and then we need to convert them to DTO objects
                     .map(stat -> new UploadFileEvent(
                       toFileId(stat.getChecksum(), stat.getObjectKey()),
                       stat.getFileSize(),
                       stat.getNumberOfParts(),
                       stat.getChunkSize(),
                       stat.getDuration().toMillis(),
                       stat.getErrors().size(),
                       stat.isSuccessful()
                     ))
                     // and then we need to send them to the lens
                     .map(event -> {
                       try {
                         return lensClient.publishUploadFileEvent(buildId, event);
                       } catch (Exception e) {
                         Loggers.AGENT.warnAndDebugDetails(
                           String.format(
                             "Failed to publish upload event to the lens: buildId=%d, event=%s",
                             buildId, event), e);
                         return null;
                       }
                     })
                     .filter(Objects::nonNull)
                     .toArray(CompletableFuture[]::new);
  }

  private static String toFileId(String checksum, String objectKey) {
    if (StringUtil.isEmpty(checksum)) {
      return DigestUtils.md5Hex(objectKey);
    } else {
      return checksum;
    }
  }

  @NotNull
  private static UploadInfoEvent buildUploadInfoEvent(@NotNull Collection<UploadStatistics> statistics, @NotNull Duration totalUploadDuration) {
    UploadInfoEvent uploadInfoEvent = new UploadInfoEvent();
    for (UploadStatistics stat : statistics) {
      uploadInfoEvent.setTotalSize(uploadInfoEvent.getTotalSize() + stat.getFileSize());
      uploadInfoEvent.setNumberOfFiles(uploadInfoEvent.getNumberOfFiles() + 1);
    }
    uploadInfoEvent.setDuration(totalUploadDuration.toMillis());
    return uploadInfoEvent;
  }
}
