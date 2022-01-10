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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import jetbrains.buildServer.artifacts.s3.FileUploadInfo;
import jetbrains.buildServer.artifacts.s3.S3Configuration;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.artifacts.s3.exceptions.FileUploadFailedException;
import jetbrains.buildServer.artifacts.s3.publish.S3FileUploader;
import jetbrains.buildServer.artifacts.s3.publish.logger.S3UploadLogger;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.CloseableFixedThreadPoolExecutor;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.HttpClientUtil;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.LowLevelS3Client;
import jetbrains.buildServer.util.ExceptionUtil;
import jetbrains.buildServer.util.amazon.retry.Retrier;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.util.amazon.retry.Retrier.defaultRetrier;

public class S3SignedUrlFileUploader extends S3FileUploader {
  @NotNull
  private static final Logger LOGGER = Logger.getInstance(S3SignedUrlFileUploader.class.getName());
  @NotNull
  private final Supplier<PresignedUrlsProviderClient> myPresignedUrlsProviderClient;
  private final S3PresignedUploadFactory myUploadFactory;

  public S3SignedUrlFileUploader(@NotNull final S3Configuration s3Configuration,
                                 @NotNull final S3UploadLogger s3UploadLogger,
                                 @NotNull final Supplier<PresignedUrlsProviderClient> presignedUrlsProviderClient,
                                 @NotNull final S3PresignedUploadFactory uploadFactory) {
    super(s3Configuration, s3UploadLogger);
    myPresignedUrlsProviderClient = presignedUrlsProviderClient;
    myUploadFactory = uploadFactory;
  }

  @Override
  public void upload(@NotNull Map<File, String> filesToUpload, @NotNull Supplier<String> interrupter, Consumer<FileUploadInfo> uploadInfoConsumer) {
    LOGGER.debug(() -> "Publishing artifacts using S3 configuration " + myS3Configuration);

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
      normalizedObjectPaths.put(objectKey, FileWithArtifactPath.create(artifactPath, file));
    }

    final jetbrains.buildServer.util.amazon.S3Util.S3AdvancedConfiguration advancedConfiguration = myS3Configuration.getAdvancedConfiguration();
    final Retrier retrier = defaultRetrier(advancedConfiguration.getRetriesNum(), advancedConfiguration.getRetryDelay(), LOGGER);

    try (final CloseableFixedThreadPoolExecutor executor = new CloseableFixedThreadPoolExecutor(advancedConfiguration.getNThreads(), "S3 Artifact upload",
                                                                                                true);
         final LowLevelS3Client lowLevelS3Client = createAwsClient(myS3Configuration);
         final S3SignedUploadManager uploadManager = new S3SignedUploadManager(myPresignedUrlsProviderClient.get(), advancedConfiguration, normalizedObjectPaths.keySet())) {

      LOGGER.debug("Publishing [" + filesToUpload.keySet().stream().map(f -> f.getName()).collect(Collectors.joining(",")) + "] to S3");

      final List<CompletableFuture<Void>> futures = new ArrayList<>();
      final PresignedUploadProgressListenerImpl progressListener = new PresignedUploadProgressListenerImpl(myLogger, uploadManager, interrupter);

      for (Map.Entry<String, FileWithArtifactPath> objectKeyToFileWithArtifactPath : normalizedObjectPaths.entrySet()) {
        final String artifactPath = objectKeyToFileWithArtifactPath.getValue().getArtifactPath();
        final String key = objectKeyToFileWithArtifactPath.getKey();
        final File file = objectKeyToFileWithArtifactPath.getValue().getFile();

        final Callable<FileUploadInfo> upload = myUploadFactory.create(artifactPath, key, file, advancedConfiguration, uploadManager, lowLevelS3Client, progressListener);

        futures.add(CompletableFuture.supplyAsync(() -> retrier.execute(upload), executor).handleAsync(handleUploadResult(uploadInfoConsumer, executor)));
      }
      CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
    } catch (Throwable e) {
      Throwable th = e instanceof ExecutionException ? e.getCause() : e;
      if (isPublishingInterruptedException(th)) {
        LOGGER.info("Publishing is interrupted " + th.getMessage(), th);
      } else if (th instanceof FileUploadFailedException) {
        throw (FileUploadFailedException)th;
      } else {
        LOGGER.warnAndDebugDetails("Got error while uploading artifacts " + th.getMessage(), th);
        throw new FileUploadFailedException(th.getMessage(), false, th);
      }
    }
  }

  @NotNull
  private BiFunction<FileUploadInfo, Throwable, Void> handleUploadResult(Consumer<FileUploadInfo> uploadInfoConsumer,
                                                                         CloseableFixedThreadPoolExecutor executor) {
    return (uploadInfo, e) -> {
      if (e != null) {
        Throwable th = e instanceof CompletionException ? e.getCause() : e;
        logPublishingError(th);
        if (isPublishingInterruptedException(th)) {
          shutdownExecutor(executor);
        } else {
          ExceptionUtil.rethrowAsRuntimeException(th);
        }
      } else {
        try {
          uploadInfoConsumer.accept(uploadInfo);
        } catch (Throwable t) {
          LOGGER.warnAndDebugDetails("Failed to send artifact upload information to consumer", t);
        }
      }
      return null;
    };
  }

  private boolean isExecutorTerminating(CloseableFixedThreadPoolExecutor executor) {
    return executor.isShutdown() || executor.isTerminated() || executor.isTerminating();
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
    if (errorCodeException != null && errorCodeException.isUploadInterrupted()) {
      return true;
    }
    return ExceptionUtil.getCause(e, InterruptedException.class) != null || ExceptionUtil.getCause(e, PublishingInterruptedException.class) != null;
  }

  private void shutdownExecutor(@NotNull final CloseableFixedThreadPoolExecutor executor) {
    if (!isExecutorTerminating(executor)) {
      LOGGER.debug("Shutting down artifact publishing pool");
      executor.shutdownNow();
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
      myUpload = upload;
    }

    @Override
    public void onPartUploadFailed(@NotNull Exception e) {
      myS3UploadLogger.partUploadFailed(myUpload.description(), e.getMessage());
    }

    @Override
    public void onPartUploadSuccess(@NotNull String uploadUrl) {
      myS3UploadLogger.partUploadFinished(myUpload.description(), uploadUrl, myUpload.getFinishedPercentage());
    }

    @Override
    public void onFileUploadFailed(@NotNull Exception e) {
      myS3UploadLogger.uploadFailed(myUpload.description(), e.getMessage());
      myUploadManager.onUploadFailed(myUpload);
    }

    @Override
    public void onFileUploadSuccess(@NotNull String uploadUrl) {
      myS3UploadLogger.uploadFinished(myUpload.description(), uploadUrl);
      myUploadManager.onUploadSuccess(myUpload);
    }

    @Override
    public void beforeUploadStarted() {
      checkInterrupted();
      myS3UploadLogger.uploadStarted(myUpload.description());
    }

    @Override
    public void beforePartUploadStarted(int partNumber) {
      checkInterrupted();
      myS3UploadLogger.partUploadStarted(myUpload.description(), partNumber);
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
