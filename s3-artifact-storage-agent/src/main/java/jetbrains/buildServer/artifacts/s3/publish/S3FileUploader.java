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

import java.io.File;
import java.util.Collection;
import java.util.Map;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.artifacts.ArtifactDataInstance;
import jetbrains.buildServer.util.amazon.S3Util;
import org.jetbrains.annotations.NotNull;

public abstract class S3FileUploader {
  @NotNull
  public abstract Collection<ArtifactDataInstance> publish(@NotNull final AgentRunningBuild build,
                                                           @NotNull final String pathPrefix,
                                                           @NotNull final Map<File, String> filesToPublish);

  @NotNull
  public S3Util.S3AdvancedConfiguration configuration(@NotNull final Map<String, String> configuration) {
    return new S3Util.S3AdvancedConfiguration()
      .withNumberOfRetries(jetbrains.buildServer.artifacts.s3.S3Util.getNumberOfRetries(configuration))
      .withRetryDelayMs(jetbrains.buildServer.artifacts.s3.S3Util.getRetryDelayInMs(configuration))
      .withPresignedUrlsChunkSize(jetbrains.buildServer.artifacts.s3.S3Util.getPresignedUploadUrlChunkSize(configuration))
      .withMinimumUploadPartSize(jetbrains.buildServer.artifacts.s3.S3Util.getMinimumUploadPartSize(configuration))
      .withMultipartUploadThreshold(jetbrains.buildServer.artifacts.s3.S3Util.getMultipartUploadThreshold(configuration))
      .withPresignedMultipartUploadEnabled(jetbrains.buildServer.artifacts.s3.S3Util.getPresignedMultipartUploadEnabled(configuration))
      .withConnectionTimeout(jetbrains.buildServer.artifacts.s3.S3Util.getConnectionTimeout(configuration))
      .withNumberOfThreads(jetbrains.buildServer.artifacts.s3.S3Util.getNumberOfThreads(configuration))
      .withUrlTtlSeconds(jetbrains.buildServer.artifacts.s3.S3Util.getUrlTtlSeconds(configuration))
      .withShutdownClient();
  }
}
