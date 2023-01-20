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

package jetbrains.buildServer.artifacts.s3.publish.presigned.upload;

import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
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
import jetbrains.buildServer.artifacts.s3.publish.UploadStatistics;
import jetbrains.buildServer.artifacts.s3.publish.logger.S3UploadLogger;
import jetbrains.buildServer.artifacts.s3.publish.logger.StatisticsLogger;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.CloseableForkJoinPoolAdapter;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.HttpClientUtil;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.LowLevelS3Client;
import jetbrains.buildServer.util.ExceptionUtil;
import jetbrains.buildServer.util.amazon.retry.Retrier;
import org.apache.commons.codec.digest.DigestUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.util.amazon.retry.Retrier.defaultRetrier;

public class S3SignedUrlFileUploader extends S3FileUploader {
  @NotNull
  private static final Logger LOGGER = Logger.getInstance(S3SignedUrlFileUploader.class.getName());
  @NotNull
  private final Supplier<PresignedUrlsProviderClient> myPresignedUrlsProviderClient;

  public S3SignedUrlFileUploader(@NotNull final S3Configuration s3Configuration,
                                 @NotNull final S3UploadLogger s3UploadLogger,
                                 @NotNull final Supplier<PresignedUrlsProviderClient> presignedUrlsProviderClient) {
    super(s3Configuration, s3UploadLogger);
    myPresignedUrlsProviderClient = presignedUrlsProviderClient;
  }

  @Override
  public Collection<UploadStatistics> upload(@NotNull Map<File, String> filesToUpload,
                                             @NotNull Supplier<String> interrupter,
                                             Consumer<FileUploadInfo> uploadInfoConsumer) {
    LOGGER.debug(() -> "Publishing artifacts using S3 configuration " + myS3Configuration);

    final boolean consistencyCheckEnabled = myS3Configuration.getAdvancedConfiguration().isConsistencyCheckEnabled();
    final Map<String, String> precalculatedDigests = new HashMap<>();

    final Map<String, FileWithArtifactPath> normalizedObjectPaths = new HashMap<>();
    for (Map.Entry<File, String> entry : filesToUpload.entrySet()) {
      final File file = entry.getKey();
      final String artifactPath = S3Util.normalizeArtifactPath(entry.getValue(), file);
      final String objectKey = myS3Configuration.getPathPrefix() + artifactPath;

      final FileWithArtifactPath existingMapping = normalizedObjectPaths.get(objectKey);
      if (existingMapping != null && !existingMapping.getFile().equals(file)) {
        myLogger.warn("Found clashing artifacts path: " + artifactPath + " leading to different files [" + existingMapping.getFile().getPath() + "," + file.getPath() + "].\n" +
                      "Only the last file will be uploaded to the specified artifact path.");
      }
      try {
        if (consistencyCheckEnabled && !precalculatedDigests.containsKey(objectKey) && !isMultipartUpload(file)) {
          precalculatedDigests.put(objectKey, getDigest(file));
        }
      } catch (IOException e) {
        LOGGER.warnAndDebugDetails("Failed to calculate digest for " + file, e);
      }
      normalizedObjectPaths.put(objectKey, FileWithArtifactPath.create(artifactPath, file));
    }

    final StatisticsLogger statisticsLogger = new StatisticsLogger();

    final Retrier retrier = defaultRetrier(myS3Configuration.getAdvancedConfiguration().getRetriesNum(), myS3Configuration.getAdvancedConfiguration().getRetryDelay(), LOGGER);

    try (final CloseableForkJoinPoolAdapter forkJoinPool = new CloseableForkJoinPoolAdapter(myS3Configuration.getAdvancedConfiguration().getNThreads());
         final LowLevelS3Client lowLevelS3Client = createAwsClient(myS3Configuration);
         final S3SignedUploadManager uploadManager = new S3SignedUploadManager(myPresignedUrlsProviderClient.get(),
                                                                               myS3Configuration.getAdvancedConfiguration(),
                                                                               normalizedObjectPaths.keySet(),
                                                                               precalculatedDigests)) {

      LOGGER.debug("Publishing [" + filesToUpload.keySet().stream().map(f -> f.getName()).collect(Collectors.joining(",")) + "] to S3");
      normalizedObjectPaths.entrySet()
                           .stream()
                           .map(objectKeyToFileWithArtifactPath -> {
                             try {
                               return forkJoinPool.submit(() -> retrier
                                 .execute(createUpload(interrupter, statisticsLogger, lowLevelS3Client, uploadManager, objectKeyToFileWithArtifactPath)));
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
                           .forEach(uploadInfo -> {
                             try {
                               uploadInfoConsumer.accept(uploadInfo);
                             } catch (Throwable t) {
                               LOGGER.warnAndDebugDetails("Failed to send artifact upload information to consumer", t);
                             }
                           });
    } catch (Throwable th) {
      if (isPublishingInterruptedException(th)) {
        LOGGER.info("Publishing is interrupted " + th.getMessage(), th);
      } else {
        if (th instanceof FileUploadFailedException) {
          throw th;
        }
        LOGGER.warnAndDebugDetails("Got error while uploading artifacts " + th.getMessage(), th);
        throw new FileUploadFailedException(th.getMessage(), false, th);
      }
    }
    return statisticsLogger.getAllRecords();
  }

  @NotNull
  private S3PresignedUpload createUpload(@NotNull Supplier<String> interrupter,
                                         StatisticsLogger statisticsLogger,
                                         LowLevelS3Client lowLevelS3Client,
                                         S3SignedUploadManager uploadManager,
                                         Map.Entry<String, FileWithArtifactPath> objectKeyToFileWithArtifactPath) throws IOException {
    if (isMultipartUpload(objectKeyToFileWithArtifactPath.getValue().getFile())) {
      return new S3PresignedMultipartUpload(objectKeyToFileWithArtifactPath.getValue().getArtifactPath(),
                                            objectKeyToFileWithArtifactPath.getKey(),
                                            objectKeyToFileWithArtifactPath.getValue().getFile(),
                                            myS3Configuration.getAdvancedConfiguration(),
                                            uploadManager,
                                            lowLevelS3Client,
                                            new PresignedUploadProgressListenerImpl(myLogger, uploadManager, interrupter, statisticsLogger));
    } else {
      return new S3PresignedUpload(objectKeyToFileWithArtifactPath.getValue().getArtifactPath(),
                                   objectKeyToFileWithArtifactPath.getKey(),
                                   objectKeyToFileWithArtifactPath.getValue().getFile(),
                                   myS3Configuration.getAdvancedConfiguration(),
                                   uploadManager,
                                   lowLevelS3Client,
                                   new PresignedUploadProgressListenerImpl(myLogger, uploadManager, interrupter, statisticsLogger));
    }
  }

  public boolean isMultipartUpload(File file) throws IOException {
    final long threshold = myS3Configuration.getAdvancedConfiguration().getMultipartUploadThreshold();
    final long chunkSize = myS3Configuration.getAdvancedConfiguration().getMinimumUploadPartSize();
    final boolean isMultipartEnabled = myS3Configuration.getAdvancedConfiguration().isPresignedMultipartUploadEnabled();
    return isMultipartEnabled && file.length() > threshold * 1.2 && file.length() > chunkSize;
  }

  @NotNull
  private String getDigest(@NotNull File file) throws IOException {
    try (final InputStream in = Files.newInputStream(file.toPath())) {
      byte[] digest = DigestUtils.md5(in);
      return Base64.getEncoder().encodeToString(digest);
    }
  }

  private boolean isPoolTerminating(CloseableForkJoinPoolAdapter forkJoinPool) {
    return forkJoinPool.isShutdown() || forkJoinPool.isTerminated() || forkJoinPool.isTerminating();
  }

  private void logPublishingError(@NotNull final Throwable e) {
    if (isPublishingInterruptedException(e)) {
      LOGGER.debug("Artifact upload has been interrupted, will not continue with current upload");
    } else {
      LOGGER.infoAndDebugDetails("Got exception while waiting for upload to finish, the upload will not continue and the artifact will be ignored", e);
    }
  }

  public static boolean isPublishingInterruptedException(@NotNull Throwable e) {
    final HttpClientUtil.HttpErrorCodeException errorCodeException = ExceptionUtil.getCause(e, HttpClientUtil.HttpErrorCodeException.class);
    if (errorCodeException != null && errorCodeException.isUploadInterrupted()) {
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
  private LowLevelS3Client createAwsClient(@NotNull final S3Configuration s3Configuration) {
    return new LowLevelS3Client(s3Configuration);
  }

  private static class PresignedUploadProgressListenerImpl implements PresignedUploadProgressListener {
    @NotNull
    private final S3UploadLogger myS3UploadLogger;
    @NotNull
    private final S3SignedUploadManager myUploadManager;
    @NotNull
    private final Supplier<String> myInterrupter;
    private final StatisticsLogger myStatisticsLogger;
    private S3PresignedUpload myUpload;

    private PresignedUploadProgressListenerImpl(@NotNull final S3UploadLogger uploadLogger,
                                                @NotNull final S3SignedUploadManager uploadManager,
                                                @NotNull final Supplier<String> interrupter, @NotNull StatisticsLogger statisticsLogger) {
      myS3UploadLogger = uploadLogger;
      myUploadManager = uploadManager;
      myInterrupter = interrupter;
      myStatisticsLogger = statisticsLogger;
    }

    @Override
    public void setUpload(@NotNull S3PresignedUpload upload) {
      myUpload = upload;
    }

    @Override
    public void partsSeparated(@NotNull Duration duration) {
      myStatisticsLogger.partsSeparated(myUpload.description(), duration);
    }

    @Override
    public void urlsGenerated(@NotNull Duration duration) {
      myStatisticsLogger.urlsGenerated(myUpload.description(), duration);
    }

    @Override
    public void onPartUploadFailed(@NotNull Throwable e) {
      myS3UploadLogger.warn("Upload chunk " + myUpload.description() + " failed with error: " + e.getMessage());
      myStatisticsLogger.uploadFailed(myUpload.description(), e.getMessage(), Instant.now());
    }

    @Override
    public void onPartUploadSuccess(@NotNull String uploadUrl) {
      myStatisticsLogger.uploadFinished(myUpload.description(), Instant.now());
    }

    @Override
    public void onFileUploadFailed(@NotNull String message, boolean isRecoverable) {
      myS3UploadLogger.warn("Upload " + myUpload.description() + " failed with error: " + message);
      myStatisticsLogger.uploadFailed(myUpload.description(), message, Instant.now());
      myUploadManager.onUploadFailed(myUpload, isRecoverable);
    }

    @Override
    public void onFileUploadSuccess(@NotNull String uploadUrl) {
      myStatisticsLogger.uploadFinished(myUpload.description(), Instant.now());
      myUploadManager.onUploadSuccess(myUpload);
    }

    @Override
    public void beforeUploadStarted() {
      checkInterrupted();
      myStatisticsLogger.uploadStarted(myUpload.description(), Instant.now());
    }

    @Override
    public void beforePartUploadStarted(int partNumber) {
      checkInterrupted();
      myStatisticsLogger.uploadStarted(myUpload.description(), Instant.now());
    }

    private void checkInterrupted() {
      final String interruptedReason = myInterrupter.get();
      if (interruptedReason != null) {
        throw new PublishingInterruptedException(interruptedReason);
      }
    }
  }

  private static class FileWithArtifactPath {
    private final String myArtifactPath;
    private final File myFile;

    private FileWithArtifactPath(@NotNull final String artifactPath, @NotNull final File file) {
      myArtifactPath = artifactPath;
      myFile = file;
    }

    public String getArtifactPath() {
      return myArtifactPath;
    }

    public File getFile() {
      return myFile;
    }

    public static FileWithArtifactPath create(@NotNull final String artifactPath, @NotNull final File file) {
      return new FileWithArtifactPath(artifactPath, file);
    }
  }
}
