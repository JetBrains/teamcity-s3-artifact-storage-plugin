package jetbrains.buildServer.artifacts.s3.publish.presigned.upload;

import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.SSLException;
import jetbrains.buildServer.artifacts.s3.FileUploadInfo;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.exceptions.FileUploadFailedException;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.HttpClientUtil;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.LowLevelS3Client;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.Throwables;
import jetbrains.buildServer.util.amazon.S3Util;
import jetbrains.buildServer.util.retry.AbortRetriesException;
import jetbrains.buildServer.util.retry.Retrier;
import jetbrains.buildServer.util.retry.impl.AbortingListener;
import jetbrains.buildServer.util.retry.impl.LoggingRetrierListener;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class S3PresignedUpload implements Callable<FileUploadInfo> {
  @NotNull
  private static final Logger LOGGER = Logger.getInstance(S3PresignedUpload.class);
  @NotNull
  protected final String myArtifactPath;
  @NotNull
  protected final String myObjectKey;
  @NotNull
  protected final File myFile;
  @NotNull
  protected final LowLevelS3Client myLowLevelS3Client;
  @NotNull
  protected final S3SignedUploadManager myS3SignedUploadManager;
  @NotNull
  protected final PresignedUploadProgressListener myProgressListener;
  @NotNull
  protected final AtomicLong myRemainingBytes = new AtomicLong();
  @NotNull
  protected final AtomicLong myUploadedBytes = new AtomicLong();
  @NotNull
  protected final Retrier myRetrier;

  /*
   * TTL for presigned url in seconds.
   *
   * This is a thread local variable because it is used in the retrier.
   * It is better to use a thread local variable, while top-level retrier will recreate the instance for each try.
   * So any AtomicReference will also be recreated.
   */
  protected static final ThreadLocal<Long> myTtl = new ThreadLocal<>();

  protected final Class<? extends Exception>[] arrayOfRetriableErrors = new Class[]{
    InterruptedException.class,
    ExecutionException.class,
    SSLException.class,
    UnknownHostException.class,
    SocketException.class,
    InterruptedIOException.class,
    IOException.class
  };

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
    myProgressListener = progressListener;
    // initialize TTL with value from configuration. without this line, default TTL will be used for the first PUT request.
    if (myTtl.get() == null) {
      myTtl.set((long)configuration.getUrlTtlSeconds());
    }
    myRetrier = Retrier.withRetries(configuration.getRetriesNum(), Retrier.DelayStrategy.linearBackOff(configuration.getRetryDelay()))
                       .registerListener(new LoggingRetrierListener(LOGGER))
                       .registerListener(
                         new AbortingListener(arrayOfRetriableErrors) {
                           @Override
                           public <T> void onFailure(@NotNull   Callable<T> callable, int retry, @NotNull Exception failure) {
                             Exception e = stripRootCause(failure);

                             if (S3SignedUrlFileUploader.isPublishingInterruptedException(e) ||
                                 // a broken pipe means that the server closed connection
                                 // in some cases it means that expiration date already passed
                                 // it is better to re-generate the request
                                 (e instanceof SocketException && e.getMessage() != null && e.getMessage().contains("Broken pipe"))) {
                               throw new AbortRetriesException(e);
                             }
                             if (e instanceof HttpClientUtil.HttpErrorCodeException) {
                               if (((HttpClientUtil.HttpErrorCodeException)e).getResponseCode() == 403 && e.getMessage().contains("Request has expired")) {
                                 final Long prev = myTtl.get();
                                 // null check for safety
                                 if (prev == null) {
                                   myTtl.set(Long.min(2L * configuration.getUrlTtlSeconds(), S3Constants.S3_AMAZON_REQUEST_TIMEOUT_CAP_IN_SECONDS));
                                 } else {
                                   myTtl.set(Long.min(2L * prev, S3Constants.S3_AMAZON_REQUEST_TIMEOUT_CAP_IN_SECONDS));
                                 }
                               } else if (isRecoverable(e)) {
                                 // recoverable error, retry
                                 return;
                               }

                               throw new AbortRetriesException(e);
                             }

                             super.onFailure(callable, retry, e);
                           }
                         });

    progressListener.setUpload(this);
  }

  @Contract("null -> null; !null -> !null")
  protected static Exception stripRootCause(Exception e) {
    try {
      return (Exception)Throwables.getRootCause(e);
    } catch (Exception ignored) {
      // do nothing
    }
    return e;
  }

  @Override
  public FileUploadInfo call() {
    try {
      if (!myFile.exists()) {
        myProgressListener.onFileUploadFailed("File does not exist", false);
        return null;
      }
      myRemainingBytes.set(myFile.length());
      myUploadedBytes.set(0);
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

  protected String upload() throws IOException {
    myProgressListener.beforeUploadStarted();
    String result;
    LOGGER.debug(() -> "Uploading artifact " + myArtifactPath + " using regular upload");
    try {
      final S3SignedUploadManager.SignedUrlInfo signedUrlInfo = myS3SignedUploadManager.getUrlWithDigest(myObjectKey, myTtl.get());
      final Long fetchTiming = signedUrlInfo.getFetchTiming();
      myProgressListener.urlsGenerated(Duration.ofNanos(fetchTiming));

      String url = signedUrlInfo.getUrl();
      String digest = signedUrlInfo.getDigest();
      String etag = myRetrier.execute(() -> myLowLevelS3Client.uploadFile(url, myFile, digest).get());
      myRemainingBytes.getAndAdd(-myFile.length());
      myProgressListener.onFileUploadSuccess(digest);
      result = etag;
    } catch (final Exception e) {
      myProgressListener.onFileUploadFailed(e.toString(), false);
      throw e;
    }

    return result;
  }

  @NotNull
  protected String stripQuery(@NotNull String url) {
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

  private boolean isRecoverable(Exception e) {
    return (e instanceof HttpClientUtil.HttpErrorCodeException) && (((HttpClientUtil.HttpErrorCodeException)e).isRecoverable());
  }
}
