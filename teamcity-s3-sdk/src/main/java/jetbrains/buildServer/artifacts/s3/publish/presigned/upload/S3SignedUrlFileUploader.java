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

package jetbrains.buildServer.artifacts.s3.publish.presigned.upload;

import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import jetbrains.buildServer.artifacts.s3.FileUploadInfo;
import jetbrains.buildServer.artifacts.s3.S3Configuration;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.artifacts.s3.exceptions.FileUploadFailedException;
import jetbrains.buildServer.artifacts.s3.publish.S3FileUploader;
import jetbrains.buildServer.artifacts.s3.publish.logger.S3UploadLogger;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.CloseableForkJoinPoolAdapter;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.HttpClientUtil;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.LowLevelS3Client;
import jetbrains.buildServer.http.HttpUtil;
import jetbrains.buildServer.util.ExceptionUtil;
import jetbrains.buildServer.util.amazon.S3Util.S3AdvancedConfiguration;
import jetbrains.buildServer.util.amazon.retry.Retrier;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.util.amazon.retry.Retrier.defaultRetrier;

public class S3SignedUrlFileUploader extends S3FileUploader {
  @NotNull
  private static final Logger LOGGER = Logger.getInstance(S3SignedUrlFileUploader.class.getName());
  @NotNull
  private final TeamCityConnectionConfiguration myTeamCityConnectionConfiguration;

  public S3SignedUrlFileUploader(@NotNull final S3Configuration s3Configuration,
                                 @NotNull final S3UploadLogger s3UploadLogger,
                                 @NotNull final TeamCityConnectionConfiguration teamCityConnectionConfiguration) {
    super(s3Configuration, s3UploadLogger);
    this.myTeamCityConnectionConfiguration = teamCityConnectionConfiguration;
  }

  @NotNull
  @Override
  public Collection<FileUploadInfo> upload(@NotNull final Map<File, String> filesToUpload, @NotNull final Supplier<String> interrupter) {
    final Map<File, String> fileToNormalizedArtifactPathMap = new HashMap<>();
    final Map<File, String> fileToS3ObjectKeyMap = new HashMap<>();
    LOGGER.debug(() -> "Publishing artifacts using S3 configuration " + myS3Configuration);

    for (Map.Entry<File, String> entry : filesToUpload.entrySet()) {
      final String normalizeArtifactPath = S3Util.normalizeArtifactPath(entry.getValue(), entry.getKey());
      fileToNormalizedArtifactPathMap.put(entry.getKey(), normalizeArtifactPath);
      fileToS3ObjectKeyMap.put(entry.getKey(), myS3Configuration.getPathPrefix() + normalizeArtifactPath);
    }

    final Retrier retrier = defaultRetrier(myS3Configuration.getAdvancedConfiguration().getRetriesNum(), myS3Configuration.getAdvancedConfiguration().getRetryDelay(), LOGGER);

    try (final CloseableForkJoinPoolAdapter forkJoinPool = new CloseableForkJoinPoolAdapter(myS3Configuration.getAdvancedConfiguration().getNThreads());
         final LowLevelS3Client lowLevelS3Client = createAwsClient(myS3Configuration.getAdvancedConfiguration());
         final S3SignedUploadManager uploadManager = new S3SignedUploadManager(myTeamCityConnectionConfiguration,
                                                                               myS3Configuration.getAdvancedConfiguration(),
                                                                               fileToS3ObjectKeyMap.values())) {
      try {
        return filesToUpload.keySet()
                            .stream()
                            .map(file -> {
                              try {
                                return forkJoinPool.submit(() -> retrier
                                  .execute(S3PresignedUpload.create(fileToNormalizedArtifactPathMap.get(file),
                                                                    fileToS3ObjectKeyMap.get(file),
                                                                    file,
                                                                    myS3Configuration.getAdvancedConfiguration(),
                                                                    uploadManager,
                                                                    lowLevelS3Client,
                                                                    new PresignedUploadProgressListenerImpl(myLogger, uploadManager, interrupter))));
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
                            .map((ForkJoinTask<FileUploadInfo> future) -> waitForCompletion(future, e -> {
                              logPublishingError(e);
                              if (isPublishingInterruptedException(e)) {
                                shutdownPool(forkJoinPool);
                              } else {
                                ExceptionUtil.rethrowAsRuntimeException(e);
                              }
                            }))
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
      } catch (Throwable th) {
        if (isPublishingInterruptedException(th)) {
          LOGGER.info("Publishing is interrupted " + th.getMessage(), th);
          return Collections.emptyList();
        } else {
          LOGGER.warnAndDebugDetails("Got error while uploading artifacts " + th.getMessage(), th);
          throw new FileUploadFailedException(th.getMessage(), false, th);
        }
      }
    }
  }

  private boolean isPoolTerminating(CloseableForkJoinPoolAdapter forkJoinPool) {
    return forkJoinPool.isShutdown() || forkJoinPool.isTerminated() || forkJoinPool.isTerminating();
  }

  private void logPublishingError(@NotNull final Throwable e) {
    if (isPublishingInterruptedException(e)) {
      myLogger.debug("Artifact publishing has been interrupted");
      LOGGER.debug("Artifact upload has been interrupted, will not continue with current upload");
    } else {
      myLogger.debug("Artifact publishing failed with error " + ExceptionUtil.getDisplayMessage(e));
      LOGGER.infoAndDebugDetails("Got exception while waiting for upload to finish, the upload will not continue and the artifact will be ignored", e);
    }
  }

  public static boolean isPublishingInterruptedException(@NotNull Throwable e) {
    final HttpClientUtil.HttpErrorCodeException errorCodeException = ExceptionUtil.getCause(e, HttpClientUtil.HttpErrorCodeException.class);
    if (errorCodeException != null && errorCodeException.isBuildFinishedReason()) {
      return true;
    }
    return ExceptionUtil.getCause(e, InterruptedException.class) != null || ExceptionUtil.getCause(e, PublishingInterruptedException.class) != null;
  }

  private void shutdownPool(@NotNull final CloseableForkJoinPoolAdapter pool) {
    if (!isPoolTerminating(pool)) {
      LOGGER.debug("Shutting down artifact publishing pool");
      pool.shutdownNow();
    }
  }

  @Nullable
  private FileUploadInfo waitForCompletion(@NotNull final ForkJoinTask<FileUploadInfo> future, @NotNull final Consumer<Throwable> onError) {
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
  private LowLevelS3Client createAwsClient(@NotNull final S3AdvancedConfiguration advancedConfiguration) {
    final HttpClient httpClient = HttpUtil.createHttpClient(advancedConfiguration.getConnectionTimeout());
    final HttpConnectionManager httpConnectionManager = HttpClientUtil.createConnectionManager(advancedConfiguration.getConnectionTimeout(), advancedConfiguration.getNThreads());
    httpClient.setHttpConnectionManager(httpConnectionManager);
    return new LowLevelS3Client(httpClient);
  }

  private static class PresignedUploadProgressListenerImpl implements PresignedUploadProgressListener {
    @NotNull
    private final S3UploadLogger myS3UploadLogger;
    @NotNull
    private final S3SignedUploadManager myUploadManager;
    @NotNull
    private final Supplier<String> myInterrupter;
    private S3PresignedUpload myUpload;

    private PresignedUploadProgressListenerImpl(@NotNull final S3UploadLogger uploadLogger,
                                                @NotNull final S3SignedUploadManager uploadManager,
                                                @NotNull final Supplier<String> interrupter) {
      myS3UploadLogger = uploadLogger;
      myUploadManager = uploadManager;
      myInterrupter = interrupter;
    }

    @Override
    public void setUpload(@NotNull S3PresignedUpload upload) {
      this.myUpload = upload;
    }

    @Override
    public void onPartUploadFailed(@NotNull Exception e) {
      myS3UploadLogger.warn("Upload chunk " + myUpload.description() + " failed with error " + e.getMessage());
    }

    @Override
    public void onPartUploadSuccess() {
      myS3UploadLogger.debug("Artifact upload " + myUpload.description() + " " + myUpload.getFinishedPercentage() + "%");
    }

    @Override
    public void onFileUploadFailed(@NotNull Exception e) {
      myS3UploadLogger.warn("Upload " + myUpload.description() + " failed with error " + e.getMessage());
      myUploadManager.onUploadFailed(myUpload);
    }

    @Override
    public void onFileUploadSuccess() {
      myS3UploadLogger.debug("Artifact upload " + myUpload.description() + " finished");
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
      final String interruptedReason = myInterrupter.get();
      if (interruptedReason != null) {
        throw new PublishingInterruptedException(interruptedReason);
      }
    }
  }
}
