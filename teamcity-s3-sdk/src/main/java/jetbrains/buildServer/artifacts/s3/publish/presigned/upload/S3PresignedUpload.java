package jetbrains.buildServer.artifacts.s3.publish.presigned.upload;

import com.intellij.openapi.diagnostic.Logger;
import java.io.*;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import javax.net.ssl.SSLException;
import jetbrains.buildServer.artifacts.s3.FileUploadInfo;
import jetbrains.buildServer.artifacts.s3.exceptions.FileUploadFailedException;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.*;
import jetbrains.buildServer.artifacts.s3.transport.PresignedUrlDto;
import jetbrains.buildServer.artifacts.s3.transport.PresignedUrlPartDto;
import jetbrains.buildServer.util.ExceptionUtil;
import jetbrains.buildServer.util.amazon.S3Util;
import jetbrains.buildServer.util.amazon.retry.AbortRetriesException;
import jetbrains.buildServer.util.amazon.retry.Retrier;
import jetbrains.buildServer.util.amazon.retry.impl.AbortingListener;
import jetbrains.buildServer.util.amazon.retry.impl.ExponentialDelayListener;
import jetbrains.buildServer.util.amazon.retry.impl.LoggingRetrierListener;
import org.apache.commons.codec.digest.DigestUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class S3PresignedUpload implements Callable<FileUploadInfo> {
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
  private final S3MultipartUploadFileSplitter myFileSplitter;
  private final boolean myCheckConsistency;

  @Nullable
  private String[] myEtags;

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
    myCheckConsistency = configuration.isConsistencyCheckEnabled();
    myRetrier = Retrier.withRetries(configuration.getRetriesNum())
                       .registerListener(new LoggingRetrierListener(LOGGER))
                       .registerListener(
                         new AbortingListener(SSLException.class, UnknownHostException.class, SocketException.class, InterruptedIOException.class, InterruptedException.class) {
                           @Override
                           public <T> void onFailure(@NotNull Callable<T> callable, int retry, @NotNull Exception e) {
                             if (S3SignedUrlFileUploader.isPublishingInterruptedException(e)) {
                               throw new AbortRetriesException(e);
                             }
                             if (e instanceof HttpClientUtil.HttpErrorCodeException) {
                               if (!((HttpClientUtil.HttpErrorCodeException)e).isRecoverable()) {
                                 return;
                               }
                             }
                             super.onFailure(callable, retry, e);
                           }
                         })
                       .registerListener(new ExponentialDelayListener(configuration.getRetryDelay()));

    myFileSplitter = new S3MultipartUploadFileSplitter(myChunkSizeInBytes);
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
  public FileUploadInfo call() {
    myEtags = null;
    try {
      if (!myFile.exists()) {
        throw new FileNotFoundException(myFile.getAbsolutePath());
      }
      myRemainingBytes.set(myFile.length());
      String digest = upload();
      return new FileUploadInfo(myArtifactPath, myFile.getAbsolutePath(), myFile.length(), digest);
    } catch (HttpClientUtil.HttpErrorCodeException e) {
      final String msg = "Failed to upload artifact " + myArtifactPath + ": " + e.getMessage();
      LOGGER.infoAndDebugDetails(msg, e);
      throw new FileUploadFailedException(msg, e);
    } catch (IOException e) {
      LOGGER.infoAndDebugDetails("Got exception while trying to upload file: " + e.getMessage(), e);
      throw new FileUploadFailedException(e.getMessage(), false, e);
    }
  }

  private String upload() throws IOException {
    myProgressListener.beforeUploadStarted();
    final String digest;
    if (isMultipartUpload()) {
      digest = multipartUpload();
    } else {
      digest = regularUpload();
    }
    return digest;
  }

  @NotNull
  private String multipartUpload() {
    LOGGER.debug(() -> "Multipart upload " + this + " started");
    final long totalLength = myFile.length();
    final int nParts = (int)(totalLength % myChunkSizeInBytes == 0 ? totalLength / myChunkSizeInBytes : totalLength / myChunkSizeInBytes + 1);
    myEtags = new String[nParts];

    myProgressListener.beforeUploadStarted();
    try {
      final List<FilePart> fileParts = myFileSplitter.getFileParts(myFile, nParts, myCheckConsistency);
      List<String> digests = fileParts.stream().map(FilePart::getDigest).collect(Collectors.toList());
      final PresignedUrlDto multipartUploadUrls = myS3SignedUploadManager.getMultipartUploadUrls(myObjectKey, digests);

      multipartUploadUrls.getPresignedUrlParts().forEach(presignedUrlPartDto -> {
        myProgressListener.beforePartUploadStarted(presignedUrlPartDto.getPartNumber());
        try {
          final int partIndex = presignedUrlPartDto.getPartNumber() - 1;
          final String url = presignedUrlPartDto.getUrl();
          final FilePart filePart = fileParts.get(partIndex);
          final String etag = myRetrier.execute(() -> myLowLevelS3Client.uploadFilePart(url, filePart, filePart.getDigest()));
          myRemainingBytes.getAndAdd(-filePart.getLength());
          myProgressListener.onPartUploadSuccess(stripQuery(url));
          myEtags[partIndex] = etag;
        } catch (Exception e) {
          myProgressListener.onPartUploadFailed(e);
          ExceptionUtil.rethrowAsRuntimeException(e);
        }
      });
      final Iterator<PresignedUrlPartDto> iterator = multipartUploadUrls.getPresignedUrlParts().iterator();
      String strippedUrl = iterator.hasNext() ? stripQuery(iterator.next().getUrl()) : "";
      myProgressListener.onFileUploadSuccess(strippedUrl);
      return DigestUtil.multipartDigest(getEtags());
    } catch (IOException e) {
      LOGGER.warnAndDebugDetails("Multipart upload for " + this + " failed", e);
      myProgressListener.onFileUploadFailed(e);
      throw new RuntimeException(e);
    } catch (final Exception e) {
      LOGGER.warnAndDebugDetails("Multipart upload for " + this + " failed", e);
      myProgressListener.onFileUploadFailed(e);
      throw e;
    }
  }

  @NotNull
  private String stripQuery(@NotNull String url) {
    try {
      URI uri = new URI(url);
      return new URI(uri.getScheme(),
                     uri.getAuthority(),
                     uri.getPath(),
                     null,
                     uri.getFragment()).toString();
    } catch (URISyntaxException e) {
      LOGGER.debug("Encountered error while trying to parse url", e);
      return "";
    }
  }

  @NotNull
  private String regularUpload() throws IOException {
    LOGGER.debug(() -> "Uploading artifact " + myArtifactPath + " using regular upload");
    try {
      String digest = myCheckConsistency ? getDigest(myFile) : null;
      final String url = myS3SignedUploadManager.getUrl(myObjectKey, digest);
      if (url == null) {
        throw new IOException("Could not fetch presigned URL from server");
      }
      String etag = myRetrier.execute(() -> myLowLevelS3Client.uploadFile(url, myFile, digest));
      myRemainingBytes.getAndAdd(-myFile.length());
      myProgressListener.onFileUploadSuccess(stripQuery(url));
      return etag;
    } catch (final Exception e) {
      myProgressListener.onFileUploadFailed(e);
      throw e;
    }
  }

  @NotNull
  private String getDigest(@NotNull File file) throws IOException {
    try (final InputStream in = Files.newInputStream(file.toPath())) {
      byte[] digest = DigestUtils.md5(in);
      return Base64.getEncoder().encodeToString(digest);
    }
  }

  public boolean isMultipartUpload() {
    return myMultipartEnabled && myFile.length() > myMultipartThresholdInBytes * 1.2 && myFile.length() > myChunkSizeInBytes;
  }

  @NotNull
  public List<String> getEtags() {
    return myEtags != null ? Arrays.asList(myEtags) : Collections.emptyList();
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
