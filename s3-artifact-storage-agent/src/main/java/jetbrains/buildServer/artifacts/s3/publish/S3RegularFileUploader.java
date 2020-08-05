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
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.ArtifactPublishingFailedException;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.agent.ssl.TrustedCertificatesDirectory;
import jetbrains.buildServer.artifacts.ArtifactDataInstance;
import jetbrains.buildServer.artifacts.s3.FileUploader;
import jetbrains.buildServer.artifacts.s3.PublishingFailedException;
import jetbrains.buildServer.artifacts.s3.S3Util;
import org.jetbrains.annotations.NotNull;

public class S3RegularFileUploader implements S3FileUploader {
  @NotNull
  private static final Logger LOG = Logger.getInstance(S3RegularFileUploader.class.getName());
  @NotNull
  private final BuildAgentConfiguration myBuildAgentConfiguration;

  public S3RegularFileUploader(@NotNull final BuildAgentConfiguration buildAgentConfiguration) {
    myBuildAgentConfiguration = buildAgentConfiguration;
  }

  private void removeNonExistingFiles(@NotNull final Map<File, String> filesToPublish, @NotNull final AgentRunningBuild build) {
    for (final Iterator<Map.Entry<File, String>> iterator = filesToPublish.entrySet().iterator(); iterator.hasNext(); ) {
      final Map.Entry<File, String> next = iterator.next();
      if (!next.getKey().exists()) {
        build.getBuildLogger().warning("Artifact \"" + next.getKey().getAbsolutePath() + "\" does not exist and will not be published to the server");
        iterator.remove();
      }
    }
  }

  @NotNull
  @Override
  public Collection<ArtifactDataInstance> publishFiles(@NotNull final AgentRunningBuild build,
                                                       @NotNull final String pathPrefix,
                                                       @NotNull final Map<File, String> filesToPublish) {
    removeNonExistingFiles(filesToPublish, build);
    final String homeDir = myBuildAgentConfiguration.getAgentHomeDirectory().getPath();
    final String certDirectory = TrustedCertificatesDirectory.getAllCertificatesDirectoryFromHome(homeDir);
    final int numberOfRetries = S3Util.getNumberOfRetries(build.getSharedConfigParameters());
    final int retryDelay = S3Util.getRetryDelayInMs(build.getSharedConfigParameters());
    final Map<String, String> artifactStorageSettings = build.getArtifactStorageSettings();
    try {
      return new FileUploader()
        .uploadFiles(pathPrefix, filesToPublish, certDirectory, numberOfRetries, retryDelay, artifactStorageSettings, err -> build.getBuildLogger().error(err));
    } catch (PublishingFailedException e) {
      throw new ArtifactPublishingFailedException(e.getMessage(), false, e.getCause());
    }
  }
}
