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

package jetbrains.buildServer.artifacts.s3.publish.presigned;

import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.ArtifactPublishingFailedException;
import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.artifacts.ArtifactDataInstance;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.artifacts.s3.publish.S3FileUploader;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.CloseableForkJoinPoolAdapter;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.HttpClientUtil;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.LowLevelS3Client;
import jetbrains.buildServer.http.HttpUtil;
import jetbrains.buildServer.util.ExceptionUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.amazon.S3Util.S3AdvancedConfiguration;
import jetbrains.buildServer.util.amazon.retry.AbstractRetrierEventListener;
import jetbrains.buildServer.util.amazon.retry.Retrier;
import jetbrains.buildServer.util.amazon.retry.impl.AbortingListener;
import jetbrains.buildServer.util.amazon.retry.impl.ExponentialDelayListener;
import jetbrains.buildServer.util.amazon.retry.impl.LoggingRetrierListener;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class S3SignedUrlFileUploader extends S3FileUploader {
  @NotNull
  private static final Logger LOGGER = Logger.getInstance(S3SignedUrlFileUploader.class.getName());
  @NotNull
  private final AtomicReference<AgentRunningBuild> myBuild = new AtomicReference<>();

  @NotNull
  @Override
  public Collection<ArtifactDataInstance> publish(@NotNull final AgentRunningBuild build,
                                                  @NotNull final String pathPrefix,
                                                  @NotNull final Map<File, String> filesToPublish) {
    myBuild.set(build);
    final Map<String, String> settings = build.getArtifactStorageSettings();
    final String bucketName = S3Util.getBucketName(settings);
    if (StringUtil.isEmpty(bucketName)) {
      throw new IllegalArgumentException("S3 bucket name must not be empty");
    }

    final Map<File, String> fileToNormalizedArtifactPathMap = new HashMap<>();
    final Map<File, String> fileToS3ObjectKeyMap = new HashMap<>();

    final S3AdvancedConfiguration s3Config = configuration(build.getSharedConfigParameters());

    for (Map.Entry<File, String> entry : filesToPublish.entrySet()) {
      final String normalizeArtifactPath = S3Util.normalizeArtifactPath(entry.getValue(), entry.getKey());
      fileToNormalizedArtifactPathMap.put(entry.getKey(), normalizeArtifactPath);
      fileToS3ObjectKeyMap.put(entry.getKey(), pathPrefix + normalizeArtifactPath);
    }

    final BuildProgressLogger buildLog = build.getBuildLogger();

    final Retrier retrier = Retrier.withRetries(s3Config.getRetriesNum())
                                   .registerListener(new LoggingRetrierListener(LOGGER))
                                   .registerListener(new AbstractRetrierEventListener() {
                                     @Override
                                     public <T> void onFailure(@NotNull Callable<T> callable, int retry, @NotNull Exception e) {
                                       final String retryLogPart = retry == 0 ? "" : " after " + retry + " retry";
                                       buildLog.message(callable + " failed with exception " + e.getMessage() + retryLogPart);
                                       super.onFailure(callable, retry, e);
                                     }
                                   })
                                   .registerListener(new AbortingListener(ArtifactPublishingFailedException.class))
                                   .registerListener(new AbortingListener() {
                                     @Override
                                     public <T> void onFailure(@NotNull Callable<T> callable, int retry, @NotNull Exception e) {
                                       if (e instanceof ArtifactPublishingFailedException) {
                                         if (!((ArtifactPublishingFailedException)e).isMustRepeat()) {
                                           ExceptionUtil.rethrowAsRuntimeException(e);
                                         }
                                       }
                                     }
                                   })
                                   .registerListener(new ExponentialDelayListener(s3Config.getRetryDelay()));

    try (final CloseableForkJoinPoolAdapter forkJoinPool = new CloseableForkJoinPoolAdapter(s3Config.getNThreads());
         final LowLevelS3Client lowLevelS3Client = createAwsClient(s3Config);
         final S3SignedUploadManager signedUrlProvider = new S3SignedUploadManager(teamcityConnectionConfiguration(build), fileToS3ObjectKeyMap.values())) {
      try {
        return filesToPublish.keySet()
                             .stream()
                             .map(file -> forkJoinPool.submit(() -> retrier
                               .execute(new S3PresignedUpload(
                                 fileToNormalizedArtifactPathMap.get(file),
                                 fileToS3ObjectKeyMap.get(file),
                                 file,
                                 s3Config,
                                 signedUrlProvider,
                                 lowLevelS3Client,
                                 new PresignedUploadProgressListener() {
                                   @Override
                                   public void onPartUploadFailed(@NotNull S3PresignedUpload upload, @NotNull Exception e) {
                                     buildLog.warning("Upload chunk " + upload.description() + " failed with error " + e.getMessage());
                                   }

                                   @Override
                                   public void onPartUploadSuccess(@NotNull S3PresignedUpload upload) {
                                     buildLog.debug("Artifact upload " + upload.description() + " " + upload.getFinishedPercentage() + "%");
                                   }

                                   @Override
                                   public void onFileUploadFailed(@NotNull S3PresignedUpload upload, @NotNull Exception e) throws IOException {
                                     buildLog.warning("Upload " + upload.description() + " failed with error " + e.getMessage());
                                     signedUrlProvider.onUploadFailed(upload);
                                   }

                                   @Override
                                   public void onFileUploadSuccess(@NotNull S3PresignedUpload upload) throws IOException {
                                     buildLog.debug("Artifact upload " + upload.description() + " finished");
                                     signedUrlProvider.onUploadSuccess(upload);
                                   }
                                 }))))
                             .map(this::waitForCompletion)
                             .filter(Objects::nonNull)
                             .collect(Collectors.toList());
      } catch (Throwable th) {
        LOGGER.warnAndDebugDetails("Got error while uploading artifacts " + th.getMessage(), th);
        forkJoinPool.shutdownNow();
        throw new ArtifactPublishingFailedException(th.getMessage(), false, th);
      }
    }
  }

  @Nullable
  private ArtifactDataInstance waitForCompletion(@NotNull final ForkJoinTask<ArtifactDataInstance> future) {
    try {
      return future.get();
    } catch (InterruptedException e) {
      myBuild.get().getBuildLogger().message("Artifact publishing got interrupted " +  e.getMessage());
      LOGGER.info("Artifact upload has been interrupted, will not continue with current upload");
      return null;
    } catch (ExecutionException e) {
      myBuild.get().getBuildLogger().warning("Artifact publishing failed with error " + e.getMessage());
      LOGGER.error("Got exception while waiting for upload to finish, the upload will not continue and the artifact will be ignored", e);
      return null;
    }
  }

  @NotNull
  private S3SignedUploadManager.TeamCityConnectionConfiguration teamcityConnectionConfiguration(@NotNull AgentRunningBuild build) {
    return new S3SignedUploadManager.TeamCityConnectionConfiguration(build.getAgentConfiguration().getServerUrl(),
                                                                     build.getAccessUser(),
                                                                     build.getAccessCode(),
                                                                     build.getAgentConfiguration().getServerConnectionTimeout());
  }

  @NotNull
  private LowLevelS3Client createAwsClient(@NotNull final S3AdvancedConfiguration advancedConfiguration) {
    final HttpClient httpClient = HttpUtil.createHttpClient(advancedConfiguration.getConnectionTimeout());
    final HttpConnectionManager httpConnectionManager = HttpClientUtil.createConnectionManager(advancedConfiguration.getConnectionTimeout(), advancedConfiguration.getNThreads());
    httpClient.setHttpConnectionManager(httpConnectionManager);
    return new LowLevelS3Client(httpClient);
  }
}
