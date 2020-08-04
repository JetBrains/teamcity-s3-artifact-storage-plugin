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
import java.util.*;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.ArtifactPublishingFailedException;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.agent.ssl.TrustedCertificatesDirectory;
import jetbrains.buildServer.artifacts.ArtifactDataInstance;
import jetbrains.buildServer.artifacts.s3.S3UploadingHelper;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.artifacts.s3.migration.PublishingFailedException;
import org.jetbrains.annotations.NotNull;

public class S3RegularFileUploader implements jetbrains.buildServer.artifacts.s3.publish.S3FileUploader {
  @NotNull
  private static final Logger LOG = Logger.getInstance(S3RegularFileUploader.class.getName());
  @NotNull
  private final BuildAgentConfiguration myBuildAgentConfiguration;

  public S3RegularFileUploader(@NotNull final BuildAgentConfiguration buildAgentConfiguration) {
    myBuildAgentConfiguration = buildAgentConfiguration;
  }

  @NotNull
  @Override
  public Collection<ArtifactDataInstance> publishFiles(@NotNull final AgentRunningBuild build,
                                                       @NotNull final String pathPrefix,
                                                       @NotNull final Map<File, String> filesToPublish) {
    final String homeDir = myBuildAgentConfiguration.getAgentHomeDirectory().getPath();
    final String certDirectory = TrustedCertificatesDirectory.getAllCertificatesDirectoryFromHome(homeDir);
    final Map<String, String> artifactStorageSettings = build.getArtifactStorageSettings();
    final int numberOfRetries = S3Util.getNumberOfRetries(build.getSharedConfigParameters());
    final int retryDelay = S3Util.getRetryDelayInMs(build.getSharedConfigParameters());

    try {
      return S3UploadingHelper.publishFiles(pathPrefix,
                                            filesToPublish,
                                            certDirectory,
                                            numberOfRetries,
                                            retryDelay,
                                            artifactStorageSettings,
                                            err -> build.getBuildLogger().error(err));
    } catch (PublishingFailedException e) {
      throw new ArtifactPublishingFailedException(e.getMessage(), false, e.getCause());
    }
  }
}
