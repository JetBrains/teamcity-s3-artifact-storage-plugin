/*
 * Copyright 2000-2020 JetBrains s.r.o.
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
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.ArtifactsConstants;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.agent.artifacts.AgentArtifactHelper;
import jetbrains.buildServer.artifacts.ArtifactDataInstance;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.log.LogUtil;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_PATH_PREFIX_ATTR;
import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_STORAGE_TYPE;

public class S3ArtifactsPublisher implements ArtifactsPublisher {

  private static final Logger LOG = Logger.getInstance(S3ArtifactsPublisher.class.getName());
  private static final String ERROR_PUBLISHING_ARTIFACTS_LIST = "Error publishing artifacts list";

  private final CurrentBuildTracker myTracker;
  private final AgentArtifactHelper myHelper;
  private final BuildAgentConfiguration myBuildAgentConfiguration;

  private final List<ArtifactDataInstance> myArtifacts = new ArrayList<>();
  private S3FileUploader myFileUploader;

  public S3ArtifactsPublisher(@NotNull final AgentArtifactHelper helper,
                              @NotNull final EventDispatcher<AgentLifeCycleListener> dispatcher,
                              @NotNull final CurrentBuildTracker tracker,
                              @NotNull final BuildAgentConfiguration buildAgentConfiguration) {
    myHelper = helper;
    myTracker = tracker;
    myBuildAgentConfiguration = buildAgentConfiguration;
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
    final Map<File, String> filteredMap = CollectionsUtil.filterMapByValues(map, s -> !s.startsWith(ArtifactsConstants.TEAMCITY_ARTIFACTS_DIR));

    if (!filteredMap.isEmpty()) {
      final AgentRunningBuild build = myTracker.getCurrentBuild();
      final String pathPrefix = getPathPrefix(build);
      final S3FileUploader fileUploader = getFileUploader(build);
      myArtifacts.addAll(fileUploader.publishFiles(build, pathPrefix, filteredMap));
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
    if (myFileUploader == null) {
      if (S3Util.usePreSignedUrls(build.getArtifactStorageSettings())) {
        myFileUploader = new S3SignedUrlFileUploader();
      } else {
        myFileUploader = new S3RegularFileUploader(myBuildAgentConfiguration);
      }
    }
    return myFileUploader;
  }
}
