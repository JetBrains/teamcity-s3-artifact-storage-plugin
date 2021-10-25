/*
 * Copyright 2000-2021 JetBrains s.r.o.
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.agent.artifacts.AgentArtifactHelper;
import jetbrains.buildServer.artifacts.ArtifactDataInstance;
import jetbrains.buildServer.artifacts.ArtifactTransportAdditionalHeadersProvider;
import jetbrains.buildServer.artifacts.s3.FileUploadInfo;
import jetbrains.buildServer.artifacts.s3.S3Configuration;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.publish.logger.BuildLoggerS3Logger;
import jetbrains.buildServer.artifacts.s3.publish.logger.CompositeS3UploadLogger;
import jetbrains.buildServer.artifacts.s3.publish.logger.S3Log4jUploadLogger;
import jetbrains.buildServer.artifacts.s3.publish.presigned.upload.PresignedUrlsProviderClientFactory;
import jetbrains.buildServer.artifacts.s3.publish.presigned.upload.TeamCityConnectionConfiguration;
import jetbrains.buildServer.log.LogUtil;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.amazon.retry.RecoverableException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;

import static jetbrains.buildServer.artifacts.s3.S3Constants.*;

public class S3ArtifactsPublisher implements ArtifactsPublisher {

  private static final Logger LOG = Logger.getInstance(S3ArtifactsPublisher.class.getName());
  private static final String ERROR_PUBLISHING_ARTIFACTS_LIST = "Error publishing artifacts list";

  private final CurrentBuildTracker myTracker;
  private final AgentArtifactHelper myHelper;
  private final BuildAgentConfiguration myBuildAgentConfiguration;

  private final List<ArtifactDataInstance> myArtifacts = new ArrayList<>();
  private S3FileUploader myFileUploader;
  @NotNull
  private final PresignedUrlsProviderClientFactory myPresignedUrlsProviderClientFactory;
  @NotNull
  private final ExtensionHolder myExtensionHolder;
  @Nullable
  private Collection<ArtifactTransportAdditionalHeadersProvider> myAdditionalHeadersProviders;

  @Autowired
  public S3ArtifactsPublisher(@NotNull final AgentArtifactHelper helper,
                              @NotNull final EventDispatcher<AgentLifeCycleListener> dispatcher,
                              @NotNull final CurrentBuildTracker tracker,
                              @NotNull final BuildAgentConfiguration buildAgentConfiguration,
                              @NotNull final PresignedUrlsProviderClientFactory presignedUrlsProviderClient,
                              @NotNull final ExtensionHolder extensionHolder) {
    myHelper = helper;
    myTracker = tracker;
    myBuildAgentConfiguration = buildAgentConfiguration;
    myPresignedUrlsProviderClientFactory = presignedUrlsProviderClient;
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
    final Map<File, String> filteredMap = CollectionsUtil.filterMapByValues(map, s -> myHelper.isEnabled(this, s));

    if (!filteredMap.isEmpty()) {
      final AgentRunningBuild build = myTracker.getCurrentBuild();
      final S3FileUploader fileUploader = getFileUploader(build);
      try {
        final Collection<FileUploadInfo> upload = fileUploader.upload(filteredMap, () -> {
          final BuildInterruptReason interruptReason = build.getInterruptReason();
          if (interruptReason == null) {
            return null;
          } else {
            return interruptReason.getUserDescription();
          }
        });
        upload.stream().map(fileInfo -> ArtifactDataInstance.create(fileInfo.getArtifactPath(), fileInfo.getSize())).forEach(myArtifacts::add);
      } catch (RecoverableException e) {
        throw new ArtifactPublishingFailedException(e.getMessage(), e.isRecoverable(), e);
      }
      publishArtifactsList(build);
    }

    return filteredMap.size();
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
  private S3FileUploader getFileUploader(@NotNull final AgentRunningBuild build) {
    if (myAdditionalHeadersProviders == null) {
      myAdditionalHeadersProviders = myExtensionHolder.getExtensions(ArtifactTransportAdditionalHeadersProvider.class);
    }
    if (myFileUploader == null) {
      final SettingsProcessor settingsProcessor = new SettingsProcessor(myBuildAgentConfiguration.getAgentHomeDirectory());
      final S3Configuration s3Configuration = settingsProcessor.processSettings(build.getSharedConfigParameters(), build.getArtifactStorageSettings());
      s3Configuration.setPathPrefix(getPathPrefix(build));
      myFileUploader = S3FileUploader.create(s3Configuration,
                                             CompositeS3UploadLogger.compose(new BuildLoggerS3Logger(build.getBuildLogger()), new S3Log4jUploadLogger()),
                                             () -> myPresignedUrlsProviderClientFactory.createClient(teamcityConnectionConfiguration(build), myAdditionalHeadersProviders));
    }
    return myFileUploader;
  }

  @NotNull
  private TeamCityConnectionConfiguration teamcityConnectionConfiguration(@NotNull AgentRunningBuild build) {
    return new TeamCityConnectionConfiguration(build.getAgentConfiguration().getServerUrl(),
                                               build.getArtifactStorageSettings().getOrDefault(S3Constants.S3_URLS_PROVIDER_PATH, ARTEFACTS_S3_UPLOAD_PRESIGN_URLS_HTML),
                                               build.getAccessUser(),
                                               build.getAccessCode(),
                                               build.getAgentConfiguration().getServerConnectionTimeout());
  }
}
