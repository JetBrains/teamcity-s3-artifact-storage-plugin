package jetbrains.buildServer.artifacts.s3.publish.presigned;

import com.intellij.openapi.diagnostic.Logger;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.artifacts.s3.S3Util.getContentType;

public class S3PresignedUpload implements Callable<ArtifactDataInstance> {
  @NotNull
  private static final Logger LOGGER = Logger.getInstance(S3PresignedUpload.class);
  private static final int FIVE_MB = 5 * 1024 * 1024;
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
  @NotNull
  private final List<String> etags = new ArrayList<>();
  private final int myChunkSizeInBytes;
  private final int myMultipartThresholdInBytes;
  private final boolean myMultipartEnabled;

  public S3PresignedUpload(@NotNull final String artifactPath,
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
    myChunkSizeInBytes = configuration.getMinimumUploadPartSize() != null ? configuration.getMinimumUploadPartSize().intValue() : FIVE_MB;
    myMultipartThresholdInBytes = configuration.getMultipartUploadThreshold() != null ? configuration.getMinimumUploadPartSize().intValue() : FIVE_MB;
    myMultipartEnabled = configuration.isPresignedMultipartUploadEnabled();
    myProgressListener = progressListener;
  }


  @Override
  public ArtifactDataInstance call() {
    etags.clear();
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
    final PresignedUrlDto multipartUploadUrls = myS3SignedUploadManager.getMultipartUploadUrls(myObjectKey, nParts);

    try (final FileInputStream fis = new FileInputStream(myFile);
         final BufferedInputStream bis = new BufferedInputStream(fis)) {
      multipartUploadUrls.presignedUrlParts
        .stream()
        .sorted(Comparator.comparing(presignedUrlPartDto -> presignedUrlPartDto.partNumber))
        .forEachOrdered(presignedUrlPartDto -> {
          final long remainingBytes = myRemainingBytes.get();
          final int chunkSize = (int)Math.min(remainingBytes, myChunkSizeInBytes);
          myRemainingBytes.getAndAdd(-chunkSize);
          try {
            final String etag = myLowLevelS3Client.uploadFilePart(presignedUrlPartDto.url, bis, chunkSize, getContentType(myFile));
            myProgressListener.onPartUploadSuccess(this);
            etags.add(etag);
          } catch (Exception e) {
            myProgressListener.onPartUploadFailed(this, e);
            ExceptionUtil.rethrowAsRuntimeException(e);
          }
        });
      myProgressListener.onFileUploadSuccess(this);
    } catch (final Exception e) {
      LOGGER.warnAndDebugDetails("Multipart upload for " + this + " failed", e);
      myProgressListener.onFileUploadFailed(this, e);
      throw e;
    }
  }

  private void regularUpload() throws IOException {
    LOGGER.debug(() -> "Uploading artifact " + myArtifactPath + " using regular upload");
    try {
      myLowLevelS3Client.uploadFile(myS3SignedUploadManager.getUrl(myObjectKey), myFile);
      myRemainingBytes.getAndAdd(-myFile.length());
      myProgressListener.onFileUploadSuccess(this);
    } catch (final Exception e) {
      myProgressListener.onFileUploadFailed(this, e);
      throw e;
    }
  }

  public boolean isMultipartUpload() {
    return myMultipartEnabled && myFile.length() > myMultipartThresholdInBytes * 1.2 && myFile.length() > myChunkSizeInBytes;
  }

  @NotNull
  public List<String> getEtags() {
    return etags != null ? etags : Collections.emptyList();
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
