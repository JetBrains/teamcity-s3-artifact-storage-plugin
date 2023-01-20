/*
 * Copyright 2000-2022 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.artifacts.s3.publish;

import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.agent.artifacts.AgentArtifactHelper;
import jetbrains.buildServer.agent.artifacts.ArtifactDigestInfo;
import jetbrains.buildServer.artifacts.ArtifactDataInstance;
import jetbrains.buildServer.artifacts.ArtifactTransportAdditionalHeadersProvider;
import jetbrains.buildServer.artifacts.s3.S3Configuration;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.publish.logger.BuildLoggerS3Logger;
import jetbrains.buildServer.artifacts.s3.publish.logger.CompositeS3UploadLogger;
import jetbrains.buildServer.artifacts.s3.publish.logger.S3Log4jUploadLogger;
import jetbrains.buildServer.artifacts.s3.publish.logger.StatisticsLogger;
import jetbrains.buildServer.artifacts.s3.publish.presigned.upload.PresignedUrlsProviderClientFactory;
import jetbrains.buildServer.artifacts.s3.publish.presigned.upload.TeamCityConnectionConfiguration;
import jetbrains.buildServer.log.LogUtil;
import jetbrains.buildServer.serverSide.BuildTypeOptions;
import jetbrains.buildServer.serverSide.PublishArtifactCondition;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.amazon.retry.RecoverableException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;

import static jetbrains.buildServer.artifacts.s3.S3Constants.*;

public class S3ArtifactsPublisher implements DigestProducingArtifactsPublisher {

  private static final Logger LOG = Logger.getInstance(S3ArtifactsPublisher.class.getName());
  private static final String ERROR_PUBLISHING_ARTIFACTS_LIST = "Error publishing artifacts list";
  public static final int MAX_UPLOAD_LOG_MESSAGES = 10;

  public static final String MILLIS_FORMAT = "{0,choice, 0#zero milliseconds|1#one millisecond|2#{0,number} milliseconds}";
  public static final String SECONDS_FORMAT = "{0,choice, 0#zero seconds|1#one second|2#{0,number} seconds}";

  private final CurrentBuildTracker myTracker;
  private final AgentArtifactHelper myHelper;
  private final BuildAgentConfiguration myBuildAgentConfiguration;

  private final List<ArtifactDataInstance> myArtifacts = Collections.synchronizedList(new ArrayList<>());

  private volatile S3FileUploader myFileUploader;
  @NotNull
  private final PresignedUrlsProviderClientFactory myPresignedUrlsProviderClientFactory;
  private final S3FileUploaderFactory myUploaderFactory;
  @NotNull
  private final ExtensionHolder myExtensionHolder;

  @Autowired
  public S3ArtifactsPublisher(@NotNull final AgentArtifactHelper helper,
                              @NotNull final EventDispatcher<AgentLifeCycleListener> dispatcher,
                              @NotNull final CurrentBuildTracker tracker,
                              @NotNull final BuildAgentConfiguration buildAgentConfiguration,
                              @NotNull final PresignedUrlsProviderClientFactory presignedUrlsProviderClient,
                              @NotNull final S3FileUploaderFactory uploaderFactory,
                              @NotNull final ExtensionHolder extensionHolder) {
    myHelper = helper;
    myTracker = tracker;
    myBuildAgentConfiguration = buildAgentConfiguration;
    myPresignedUrlsProviderClientFactory = presignedUrlsProviderClient;
    myUploaderFactory = uploaderFactory;
    myExtensionHolder = extensionHolder;
    dispatcher.addListener(new AgentLifeCycleAdapter() {
      @Override
      public void buildStarted(@NotNull final AgentRunningBuild runningBuild) {
        myFileUploader = null;
        myArtifacts.clear();
      }
    });
  }

  @Override
  public int publishFiles(@NotNull final Map<File, String> map) throws ArtifactPublishingFailedException {
    return publishFilesWithDigests(map, myTracker.getCurrentBuild().getBuildLogger().getFlowLogger(""), null);
  }

  @Override
  public int publishFilesWithDigests(@NotNull Map<File, String> map, @NotNull FlowLogger logger, @Nullable Consumer<ArtifactDigestInfo> digestConsumer)
    throws ArtifactPublishingFailedException {
    final Map<File, String> filteredMap = CollectionsUtil.filterMapByValues(map, s -> myHelper.isEnabled(this, s));

    if (!filteredMap.isEmpty()) {
      final AgentRunningBuild build = myTracker.getCurrentBuild();
      final S3FileUploader fileUploader = getFileUploader(build, logger);
      Collection<UploadStatistics> statistics;
      try {
        statistics = fileUploader.upload(filteredMap, () -> {
          if (isPublishingStopped(build) && build.getInterruptReason() != null) {
            return build.getInterruptReason().getUserDescription();
          } else {
            return null;
          }
        }, fileUploadInfo -> {
          myArtifacts.add(ArtifactDataInstance.create(fileUploadInfo.getArtifactPath(), fileUploadInfo.getSize()));
          if (digestConsumer != null) {
            File uploadFile = new File(fileUploadInfo.getAbsolutePath());
            try {
              digestConsumer.accept(new ArtifactDigestInfo(uploadFile, filteredMap.get(uploadFile), fileUploadInfo.getDigest()));
            } catch (Throwable t) {
              LOG.warn("Failed to send artifact upload information to digest consumer", t);
            }
          }
        });
      } catch (RecoverableException e) {
        throw new ArtifactPublishingFailedException(e.getMessage(), e.isRecoverable(), e);
      }
      publishArtifactsList(build);
      if (statistics != null) {
        if (statistics.size() > MAX_UPLOAD_LOG_MESSAGES) {
          final StatisticsLogger.SummaryStatistics stats = getSummaryStatistics(statistics);
          logger.debug(
            String.format("In total %d files uploaded. Summary upload time: %s. Average upload time per file: %s. Number of errors: %d. Logging information for first %d files",
                          stats.getFileCount(),
                          formatDuration(stats.getTotalDuration()),
                          formatDuration(stats.getAverageDuration()),
                          stats.getErrors().size(),
                          MAX_UPLOAD_LOG_MESSAGES
            ));
          logStatisticsForEach(logger, new ArrayList<>(statistics).subList(0, MAX_UPLOAD_LOG_MESSAGES));
        } else {
          logStatisticsForEach(logger, statistics);
        }
      }
    }

    return filteredMap.size();
  }

  @NotNull
  public StatisticsLogger.SummaryStatistics getSummaryStatistics(@NotNull Collection<UploadStatistics> statistics) {
    Duration totalDuration = Duration.ofMillis(0);
    int fileCount = 0;
    List<String> errors = new ArrayList<>();
    for (UploadStatistics stat : statistics) {
      totalDuration = totalDuration.plus(stat.getDuration());
      fileCount++;
      errors.addAll(stat.getErrors());
    }
    final Duration averageDuration = totalDuration.dividedBy(fileCount);
    return new StatisticsLogger.SummaryStatistics(totalDuration, averageDuration, fileCount, errors);
  }

  private void logStatisticsForEach(@NotNull FlowLogger logger, @NotNull Collection<UploadStatistics> statistics) {
    for (UploadStatistics stat : statistics) {
      final Map<String, Duration> timings = stat.getAdditionalTimings();
      String timingsInfo = "";
      if (!timings.isEmpty()) {
        timingsInfo = ". " + timings.entrySet()
                                    .stream()
                                    .map(t -> String.format("%s took: %s", t.getKey(), formatDuration(t.getValue())))
                                    .collect(Collectors.joining(". "));
      }
      logger.debug(String.format("Uploaded %s. Total upload time: %s. Number of errors: %d%s",
                                 stat.getObjectKey(),
                                 formatDuration(stat.getDuration()),
                                 stat.getErrors().size(),
                                 timingsInfo
      ));
    }
  }

  @NotNull
  private static String formatDuration(@NotNull Duration duration) {
    String format;
    if (duration.getSeconds() < 1) {
      format = MessageFormat.format(MILLIS_FORMAT, duration.toMillis());
    } else {
      format = MessageFormat.format(SECONDS_FORMAT, duration.getSeconds());
    }
    return format;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @NotNull
  @Override
  public String getType() {
    return S3_STORAGE_TYPE;
  }

  private void publishArtifactsList(@NotNull final AgentRunningBuild build) {
    if (!myArtifacts.isEmpty()) {
      final String pathPrefix = getPathPrefix(build);
      try {
        myHelper.publishArtifactList(myArtifacts, CollectionsUtil.asMap(S3_PATH_PREFIX_ATTR, pathPrefix));
      } catch (IOException e) {
        build.getBuildLogger().error(ERROR_PUBLISHING_ARTIFACTS_LIST + ": " + e.getMessage());
        LOG.warnAndDebugDetails(ERROR_PUBLISHING_ARTIFACTS_LIST + "for build " + LogUtil.describe(build), e);
      }
    }
  }

  private boolean isPublishingStopped(@NotNull final AgentRunningBuild build) {
    if (build.isInAlwaysExecutingStage()) return false;
    if (build.getBuildTypeOptionValue(BuildTypeOptions.BT_ARTIFACT_PUBLISH_CONDITION) == PublishArtifactCondition.ALWAYS) {
      return false;
    }

    BuildInterruptReason reason = build.getInterruptReason();
    return reason != null && reason != BuildInterruptReason.EXECUTION_TIMEOUT;
  }

  @NotNull
  private String getPathPrefix(@NotNull final AgentRunningBuild build) {
    final List<String> pathSegments = new ArrayList<>();
    final String prefix = build.getArtifactStorageSettings().getOrDefault(S3Constants.S3_PATH_PREFIX_SETTING, "");
    if (!StringUtil.isEmptyOrSpaces(prefix)) {
      pathSegments.add(prefix);
    }
    pathSegments.add(build.getSharedConfigParameters().get(ServerProvidedProperties.TEAMCITY_PROJECT_ID_PARAM));
    pathSegments.add(build.getBuildTypeExternalId());
    pathSegments.add(Long.toString(build.getBuildId()));
    return StringUtil.join("/", pathSegments) + "/";
  }

  @NotNull
  private S3FileUploader getFileUploader(@NotNull final AgentRunningBuild build, FlowLogger flowLogger) {
    if (myFileUploader == null) {
      Collection<ArtifactTransportAdditionalHeadersProvider> headersProviders = myExtensionHolder.getExtensions(ArtifactTransportAdditionalHeadersProvider.class);
      final SettingsProcessor settingsProcessor = new SettingsProcessor(myBuildAgentConfiguration);
      final S3Configuration s3Configuration = settingsProcessor.processSettings(build.getSharedConfigParameters(), build.getArtifactStorageSettings());
      s3Configuration.setPathPrefix(getPathPrefix(build));
      myFileUploader = myUploaderFactory.create(s3Configuration,
                                                CompositeS3UploadLogger.compose(new BuildLoggerS3Logger(flowLogger), new S3Log4jUploadLogger()),
                                                () -> myPresignedUrlsProviderClientFactory.createClient(teamcityConnectionConfiguration(build, s3Configuration), headersProviders));
    }
    return myFileUploader;
  }

  @NotNull
  private TeamCityConnectionConfiguration teamcityConnectionConfiguration(@NotNull AgentRunningBuild build, S3Configuration s3Configuration) {
    return new TeamCityConnectionConfiguration(build.getAgentConfiguration().getServerUrl(),
                                               build.getArtifactStorageSettings().getOrDefault(S3Constants.S3_URLS_PROVIDER_PATH, ARTEFACTS_S3_UPLOAD_PRESIGN_URLS_HTML),
                                               build.getAccessUser(),
                                               build.getAccessCode(),
                                               build.getNodeIdHolder(),
                                               build.getAgentConfiguration().getServerConnectionTimeout(),
                                               s3Configuration.getAdvancedConfiguration().getRetriesNum(),
                                               s3Configuration.getAdvancedConfiguration().getRetryDelay());
  }
}
