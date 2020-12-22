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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.ArtifactPublishingFailedException;
import jetbrains.buildServer.agent.BuildInterruptReason;
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
         final S3SignedUploadManager uploadManager = new S3SignedUploadManager(teamcityConnectionConfiguration(build), fileToS3ObjectKeyMap.values())) {
      try {
        return filesToPublish.keySet()
                             .stream()
                             .map(file -> {
                               try {
                                 return forkJoinPool.submit(() -> retrier
                                   .execute(S3PresignedUpload.create(fileToNormalizedArtifactPathMap.get(file),
                                                                     fileToS3ObjectKeyMap.get(file),
                                                                     file,
                                                                     s3Config,
                                                                     uploadManager,
                                                                     lowLevelS3Client,
                                                                     new PresignedUploadProgressListenerImpl(build, uploadManager))));
                               } catch (RejectedExecutionException e) {
                                 if (isPoolTerminating(forkJoinPool)) {
                                   LOGGER.debug("Artifact publishing rejected by pool shutdown");
                                 } else {
                                   LOGGER.warnAndDebugDetails("Artifact publishing rejected by pool", e);
                                 }
                                 return null;
                               }
                             })
                             .filter(Objects::nonNull)
                             .map((ForkJoinTask<ArtifactDataInstance> future) -> waitForCompletion(future, e -> {
                               if (e instanceof PublishingInterruptedException || e instanceof InterruptedException) {
                                 shutdownPool(forkJoinPool);
                               }
                               logPublishingError(e);
                             }))
                             .filter(Objects::nonNull)
                             .collect(Collectors.toList());
      } catch (Throwable th) {
        if (!(th instanceof InterruptedException)) {
          LOGGER.warnAndDebugDetails("Got error while uploading artifacts " + th.getMessage(), th);
        }
        throw new ArtifactPublishingFailedException(th.getMessage(), false, th);
      }
    }
  }

  private boolean isPoolTerminating(CloseableForkJoinPoolAdapter forkJoinPool) {
    return forkJoinPool.isShutdown() || forkJoinPool.isTerminated() || forkJoinPool.isTerminating();
  }

  private void logPublishingError(@NotNull final Throwable e) {
    if ((e instanceof HttpClientUtil.HttpErrorCodeException && ((HttpClientUtil.HttpErrorCodeException)e).isBuildFinishedReason()) || e instanceof InterruptedException) {
      myBuild.get().getBuildLogger().debug("Artifact publishing has been interrupted");
      LOGGER.debug("Artifact upload has been interrupted, will not continue with current upload");
    } else {
      myBuild.get().getBuildLogger().debug("Artifact publishing failed with error " + ExceptionUtil.getDisplayMessage(e));
      LOGGER.infoAndDebugDetails("Got exception while waiting for upload to finish, the upload will not continue and the artifact will be ignored", e);
    }
  }

  private void shutdownPool(@NotNull final CloseableForkJoinPoolAdapter pool) {
    if (!isPoolTerminating(pool)) {
      LOGGER.debug("Shutting down artifact publishing pool");
      pool.shutdownNow();
    }
  }

  @Nullable
  private ArtifactDataInstance waitForCompletion(@NotNull final ForkJoinTask<ArtifactDataInstance> future, @NotNull final Consumer<Throwable> onError) {
    try {
      return future.get();
    } catch (final ExecutionException e) {
      onError.accept(e.getCause());
      return null;
    } catch (Throwable e) {
      onError.accept(e);
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

  private static class PresignedUploadProgressListenerImpl implements PresignedUploadProgressListener {
    @NotNull
    private final AgentRunningBuild myBuild;
    @NotNull
    private final S3SignedUploadManager myUploadManager;
    private S3PresignedUpload myUpload;

    private PresignedUploadProgressListenerImpl(@NotNull final AgentRunningBuild build, @NotNull final S3SignedUploadManager uploadManager) {
      myBuild = build;
      myUploadManager = uploadManager;
    }

    @Override
    public void setUpload(@NotNull S3PresignedUpload upload) {
      this.myUpload = upload;
    }

    @Override
    public void onPartUploadFailed(@NotNull Exception e) {
      myBuild.getBuildLogger().message("Upload chunk " + myUpload.description() + " failed with error " + e.getMessage());
    }

    @Override
    public void onPartUploadSuccess() {
      myBuild.getBuildLogger().debug("Artifact upload " + myUpload.description() + " " + myUpload.getFinishedPercentage() + "%");
    }

    @Override
    public void onFileUploadFailed(@NotNull Exception e) {
      myBuild.getBuildLogger().message("Upload " + myUpload.description() + " failed with error " + e.getMessage());
      myUploadManager.onUploadFailed(myUpload);
    }

    @Override
    public void onFileUploadSuccess() {
      myBuild.getBuildLogger().debug("Artifact upload " + myUpload.description() + " finished");
      myUploadManager.onUploadSuccess(myUpload);
    }

    @Override
    public void beforeUploadStarted() {
      checkInterrupted();
    }

    @Override
    public void beforePartUploadStarted() {
      checkInterrupted();
    }

    private void checkInterrupted() {
      final BuildInterruptReason reason = myBuild.getInterruptReason();
      if (reason != null) {
        throw new PublishingInterruptedException(reason.getUserDescription());
      }
    }
  }
}
