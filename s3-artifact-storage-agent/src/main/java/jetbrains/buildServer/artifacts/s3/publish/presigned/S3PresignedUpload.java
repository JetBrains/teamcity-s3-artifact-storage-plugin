package jetbrains.buildServer.artifacts.s3.publish.presigned;

import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
import jetbrains.buildServer.agent.ArtifactPublishingFailedException;
import jetbrains.buildServer.artifacts.ArtifactDataInstance;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.HttpClientUtil;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.LowLevelS3Client;
import jetbrains.buildServer.artifacts.s3.transport.PresignedUrlDto;
import jetbrains.buildServer.util.ExceptionUtil;
import jetbrains.buildServer.util.amazon.S3Util;
import jetbrains.buildServer.util.amazon.retry.AbstractRetrierEventListener;
import jetbrains.buildServer.util.amazon.retry.Retrier;
import jetbrains.buildServer.util.amazon.retry.impl.AbortingListener;
import jetbrains.buildServer.util.amazon.retry.impl.ExponentialDelayListener;
import jetbrains.buildServer.util.amazon.retry.impl.LoggingRetrierListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class S3PresignedUpload implements Callable<ArtifactDataInstance> {
  @NotNull
  private static final Logger LOGGER = Logger.getInstance(S3PresignedUpload.class);
  @NotNull
  private final String myArtifactPath;
  @NotNull
  private final String myObjectKey;
  @NotNull
  private final File myFile;
  @NotNull
  private final LowLevelS3Client myLowLevelS3Client;
  @NotNull
  private final S3SignedUploadManager myS3SignedUploadManager;
  @NotNull
  private final PresignedUploadProgressListener myProgressListener;
  @NotNull
  private final AtomicLong myRemainingBytes = new AtomicLong();
  private final long myChunkSizeInBytes;
  private final long myMultipartThresholdInBytes;
  private final boolean myMultipartEnabled;
  @NotNull
  private final Retrier myRetrier;
  @Nullable
  private String[] etags;

  private S3PresignedUpload(@NotNull final String artifactPath,
                            @NotNull final String objectKey,
                            @NotNull final File file,
                            @NotNull final S3Util.S3AdvancedConfiguration configuration,
                            @NotNull final S3SignedUploadManager s3SignedUploadManager,
                            @NotNull final LowLevelS3Client lowLevelS3Client,
                            @NotNull final PresignedUploadProgressListener progressListener) {
    myArtifactPath = artifactPath;
    myObjectKey = objectKey;
    myFile = file;
    myS3SignedUploadManager = s3SignedUploadManager;
    myLowLevelS3Client = lowLevelS3Client;
    myChunkSizeInBytes = configuration.getMinimumUploadPartSize();
    myMultipartThresholdInBytes = configuration.getMultipartUploadThreshold();
    myMultipartEnabled = configuration.isPresignedMultipartUploadEnabled();
    myProgressListener = progressListener;
    myRetrier = Retrier.withRetries(configuration.getRetriesNum())
           .registerListener(new AbortingListener(HttpClientUtil.HttpErrorCodeException.class))
           .registerListener(new LoggingRetrierListener(LOGGER))
           .registerListener(new AbstractRetrierEventListener() {
             @Override
             public <T> void onFailure(@NotNull Callable<T> callable, int retry, @NotNull Exception e) {
               if (e instanceof HttpClientUtil.HttpErrorCodeException) {
                 if (!((HttpClientUtil.HttpErrorCodeException)e).isRecoverable()) {
                   ExceptionUtil.rethrowAsRuntimeException(e);
                 }
               }
             }
           })
           .registerListener(new ExponentialDelayListener(configuration.getRetryDelay()));
  }

  @NotNull
  public static S3PresignedUpload create(@NotNull final String artifactPath,
                                         @NotNull final String objectKey,
                                         @NotNull final File file,
                                         @NotNull final S3Util.S3AdvancedConfiguration configuration,
                                         @NotNull final S3SignedUploadManager s3SignedUploadManager,
                                         @NotNull final LowLevelS3Client lowLevelS3Client,
                                         @NotNull final PresignedUploadProgressListener progressListener) {
    final S3PresignedUpload s3PresignedUpload = new S3PresignedUpload(artifactPath, objectKey, file, configuration, s3SignedUploadManager, lowLevelS3Client, progressListener);
    progressListener.setUpload(s3PresignedUpload);
    return s3PresignedUpload;
  }

  @Override
  public ArtifactDataInstance call() {
    etags = null;
    try {
      if (!myFile.exists()) {
        throw new FileNotFoundException(myFile.getAbsolutePath());
      }
      myRemainingBytes.set(myFile.length());
      upload();
      return ArtifactDataInstance.create(myArtifactPath, myFile.length());
    } catch (HttpClientUtil.HttpErrorCodeException e) {
      final String msg = "Failed to upload artifact " + myArtifactPath + ": " + e.getMessage();
      LOGGER.infoAndDebugDetails(msg, e);
      throw new ArtifactPublishingFailedException(msg, false, e);
    } catch (IOException e) {
      LOGGER.infoAndDebugDetails("Got exception while trying to upload file: " + e.getMessage(), e);
      throw new ArtifactPublishingFailedException(e.getMessage(), false, e);
    }
  }

  private void upload() throws IOException {
    myProgressListener.beforeUploadStarted();
    if (isMultipartUpload()) {
      multipartUpload();
    } else {
      regularUpload();
    }
  }

  private void multipartUpload() throws IOException {
    LOGGER.debug(() -> "Multipart upload " + this + " started");
    final long totalLength = myFile.length();
    final int nParts = (int)(totalLength % myChunkSizeInBytes == 0 ? totalLength / myChunkSizeInBytes : totalLength / myChunkSizeInBytes + 1);
    etags = new String[nParts];
    final PresignedUrlDto multipartUploadUrls = myS3SignedUploadManager.getMultipartUploadUrls(myObjectKey, nParts);
    myProgressListener.beforeUploadStarted();
    try {
      multipartUploadUrls.presignedUrlParts.forEach(presignedUrlPartDto -> {
        myProgressListener.beforePartUploadStarted();
        try {
          final int partIndex = presignedUrlPartDto.partNumber - 1;
          final long contentLength = Math.min(myChunkSizeInBytes, myFile.length() - myChunkSizeInBytes * partIndex);
          myRemainingBytes.getAndAdd(-contentLength);
          final long start = partIndex * myChunkSizeInBytes;
          final String etag = myRetrier.execute(() -> myLowLevelS3Client.uploadFilePart(presignedUrlPartDto.url, myFile, start, contentLength));
          myProgressListener.onPartUploadSuccess();
          etags[partIndex] = etag;
        } catch (Exception e) {
          myProgressListener.onPartUploadFailed(e);
          ExceptionUtil.rethrowAsRuntimeException(e);
        }
      });
      myProgressListener.onFileUploadSuccess();
    } catch (final Exception e) {
      LOGGER.warnAndDebugDetails("Multipart upload for " + this + " failed", e);
      myProgressListener.onFileUploadFailed(e);
      throw e;
    }
  }

  private void regularUpload() throws IOException {
    LOGGER.debug(() -> "Uploading artifact " + myArtifactPath + " using regular upload");
    try {
      myRetrier.execute(() -> myLowLevelS3Client.uploadFile(myS3SignedUploadManager.getUrl(myObjectKey), myFile));
      myRemainingBytes.getAndAdd(-myFile.length());
      myProgressListener.onFileUploadSuccess();
    } catch (final Exception e) {
      myProgressListener.onFileUploadFailed(e);
      throw e;
    }
  }

  public boolean isMultipartUpload() {
    return myMultipartEnabled && myFile.length() > myMultipartThresholdInBytes * 1.2 && myFile.length() > myChunkSizeInBytes;
  }

  @NotNull
  public List<String> getEtags() {
    return etags != null ? Arrays.asList(etags) : Collections.emptyList();
  }

  @NotNull
  public String getObjectKey() {
    return myObjectKey;
  }

  @Override
  public String toString() {
    return "Artifact upload " + description();
  }

  @NotNull
  public String description() {
    return "[" + myFile.getAbsolutePath() + " => " + myObjectKey + "]";
  }

  public int getFinishedPercentage() {
    return 100 - (int)Math.round((myRemainingBytes.get() * 100.) / myFile.length());
  }
}
