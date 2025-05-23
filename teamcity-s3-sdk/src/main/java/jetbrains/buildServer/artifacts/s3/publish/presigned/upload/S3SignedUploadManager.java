package jetbrains.buildServer.artifacts.s3.publish.presigned.upload;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import jetbrains.buildServer.artifacts.s3.exceptions.FileUploadFailedException;
import jetbrains.buildServer.artifacts.s3.transport.MultipartUploadAbortRequestDto;
import jetbrains.buildServer.artifacts.s3.transport.MultipartUploadCompleteRequestDto;
import jetbrains.buildServer.artifacts.s3.transport.PresignedUrlDto;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.ExceptionUtil;
import jetbrains.buildServer.util.NamedThreadFactory;
import jetbrains.buildServer.util.UptodateValue;
import jetbrains.buildServer.util.amazon.S3Util;
import jetbrains.buildServer.util.amazon.retry.AmazonRetrier;
import jetbrains.buildServer.util.retry.Retrier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class S3SignedUploadManager {
  @NotNull
  private static final Logger LOGGER = Logger.getInstance(S3SignedUploadManager.class);
  @NotNull
  private final List<String> myS3ObjectKeys;
  @NotNull
  private final UptodateValue<Map<String, PresignedUrlDto>> myCache;
  @NotNull
  private final Map<String, String> myPrecalculatedDigests;
  private final int myMaxUrlChunkSize;
  @NotNull
  private final Lock myFetchLock = new ReentrantLock();
  @NotNull
  private final Map<String, String> myMultipartUploadIds = new ConcurrentHashMap<>();
  @NotNull
  private final String myCorrelationId = UUID.randomUUID().toString();
  @NotNull
  private final PresignedUrlsProviderClient myPresignedUrlsProviderClient;
  @NotNull
  private final Retrier myRetrier;
  @NotNull
  private final Map<String, Long> fetchTimings = new ConcurrentHashMap<>();

  public S3SignedUploadManager(@NotNull final PresignedUrlsProviderClient presignedUrlsProviderClient,
                               @NotNull final S3Util.S3AdvancedConfiguration s3Config,
                               @NotNull final Collection<String> s3ObjectKeys,
                               @NotNull Map<String, String> precalculatedDigests) {
    myPresignedUrlsProviderClient = presignedUrlsProviderClient;
    myS3ObjectKeys = new ArrayList<>(s3ObjectKeys);
    myPrecalculatedDigests = precalculatedDigests;
    myMaxUrlChunkSize = s3Config.getPresignedUrlMaxChunkSize();
    myRetrier = AmazonRetrier.defaultAwsRetrier(s3Config.getRetriesNum(), s3Config.getRetryDelay(), LOGGER);
    myCache = new UptodateValue<>(this::fetchPresignedUrlsFromProvider, s3Config.getUrlTtlSeconds() * 1000L);
  }

  @NotNull
  public SignedUrlInfo getUrlWithDigest(@NotNull final String s3ObjectKey, @Nullable Long customTtl) {
    PresignedUrlDto presignedUrlDto;
    Long fetchTime;
    if (customTtl != null) {
      final long urlRequestStart = System.nanoTime();
      presignedUrlDto = myPresignedUrlsProviderClient.getUrl(s3ObjectKey, myPrecalculatedDigests.get(s3ObjectKey), customTtl);
      final long urlRequestEnd = System.nanoTime();
      fetchTime = urlRequestEnd - urlRequestStart;
      myCache.getValue().put(s3ObjectKey, presignedUrlDto);
      fetchTimings.put(s3ObjectKey, fetchTime);
    } else {
      presignedUrlDto = myCache.getValue().get(s3ObjectKey);
      fetchTime = fetchTimings.get(s3ObjectKey);
    }
    if (presignedUrlDto == null) {
      LOGGER.info(() -> "Presigned url for object key '" + s3ObjectKey + "' wasn't found in cached result from server, cache: '" + myCache.getValue().toString() + "'");
      throw new IllegalArgumentException("Specified object key not found in cached response from server");
    }

    if (presignedUrlDto.getPresignedUrlParts().size() != 1) {
      throw new IllegalArgumentException("Specified object key requested exactly [1] presigned url while ["
                                         + presignedUrlDto.getPresignedUrlParts().size() + "] urls returned from provider");
    }
    final String url = Objects.requireNonNull(CollectionsUtil.findFirst(presignedUrlDto.getPresignedUrlParts(), CollectionsUtil.acceptAllFilter())).getUrl();
    return new SignedUrlInfo(url, myPrecalculatedDigests.get(s3ObjectKey), fetchTime);

  }


  @NotNull
  private Map<String, PresignedUrlDto> fetchPresignedUrlsFromProvider() {
    try {
      return NamedThreadFactory.executeWithNewThreadName("Fetching presigned urls for " + this, () -> {
        LOGGER.debug(() -> "Fetching presigned urls for manager " + this + " started...");
        myFetchLock.lock();
        try {
          return CollectionsUtil.split(myS3ObjectKeys, (myS3ObjectKeys.size() / myMaxUrlChunkSize) + 1)
                                                                      .stream()
                                                                      .map(keys -> {
                                                                        LOGGER.debug(() -> "Fetching chunk " + keys + " of size " + keys.size() + " of total " + myS3ObjectKeys.size() +
                                                                                " started");
                                                                        final long urlRequestStart = System.nanoTime();
                                                                        final Collection<PresignedUrlDto> urls = myRetrier.retryableMapper(
                                                                                                                             (List<String> k) -> myPresignedUrlsProviderClient.getRegularPresignedUrls(k,
                                                                                                                                                                                                          myPrecalculatedDigests))
                                                                                                                           .apply(keys);
                                                                        final long urlRequestEnd = System.nanoTime();
                                                                        final long avgFetchTime = (urlRequestEnd - urlRequestStart) / keys.size();
                                                                        for (String key : keys) {
                                                                          fetchTimings.put(key, avgFetchTime);
                                                                        }
                                                                        LOGGER.debug(() -> "Fetching chunk " + keys + " of size " + keys.size() + " of total " + myS3ObjectKeys.size() +
                                                                                " finished");
                                                                        return urls;
                                                                      })
                                                                      .flatMap(presignedUrlDto -> presignedUrlDto.stream())
                                                                      .collect(Collectors.toMap(o -> o.getObjectKey(), presignedUrlDto -> presignedUrlDto));
        } finally {
          myFetchLock.unlock();
          LOGGER.debug(() -> "Fetching presigned urls for manager " + this + " finished.");
        }
      });
    } catch (Exception e) {
      LOGGER.info("Fetching presigned urls for manager " + this + " failed.", e);
      ExceptionUtil.rethrowAsRuntimeException(e);
      return null;
    }
  }

  @NotNull
  public Pair<PresignedUrlDto, Long> getMultipartUploadUrls(@NotNull final String objectKey,
                                                            @NotNull String contentType,
                                                            @NotNull final List<String> digests,
                                                            @Nullable String uploadId, @Nullable Long ttl) {
    final long urlRequestStart = System.nanoTime();
    final PresignedUrlDto presignedUrl = myRetrier.execute(
      () -> myPresignedUrlsProviderClient.getMultipartPresignedUrl(objectKey, contentType, digests, uploadId, ttl));
    final long urlRequestEnd = System.nanoTime();
    Long fetchTiming = urlRequestEnd - urlRequestStart;
    myMultipartUploadIds.put(presignedUrl.getObjectKey(), presignedUrl.getUploadId());
    return Pair.create(presignedUrl, fetchTiming);
  }

  public void onUploadSuccess(@NotNull final S3PresignedUpload upload) {
    onUploadFinished(upload, true, false);
  }

  public void onUploadFailed(@NotNull final S3PresignedUpload upload, boolean isRecoverable) {
    onUploadFinished(upload, false, isRecoverable);
  }

  private void onUploadFinished(@NotNull final S3PresignedUpload upload, final boolean isSuccess, boolean isRecoverable) {
    LOGGER.debug("Sending " + (isSuccess ? "success" : "abort") + " multipart upload for manager " + this + " started...");
    final String uploadId = myMultipartUploadIds.get(upload.getObjectKey());
    if (uploadId != null && upload instanceof S3PresignedMultipartUpload) {
      final S3PresignedMultipartUpload multipartUpload = (S3PresignedMultipartUpload)upload;
      try {
        myRetrier.execute(() -> {
          if (isSuccess) {
            myPresignedUrlsProviderClient.completeMultipartUpload(new MultipartUploadCompleteRequestDto(multipartUpload.getObjectKey(), uploadId, multipartUpload.getEtags()));
          } else if (!isRecoverable) {
            myPresignedUrlsProviderClient.abortMultipartUpload(new MultipartUploadAbortRequestDto(multipartUpload.getObjectKey(), uploadId));
          }
        });
        LOGGER.debug("Multipart upload for " + this + " has been " + (isSuccess ? "completed" : "aborted"));
      } catch (Exception e) {
        final String message = "Failed to " + (isSuccess ? "complete" : "abort") + " multipart upload for " + this;
        LOGGER.warnAndDebugDetails(message, e);
        throw new FileUploadFailedException(message, false, e);
      }
      myS3ObjectKeys.remove(upload.getObjectKey());
      myMultipartUploadIds.remove(upload.getObjectKey());
    }
  }

  @Override
  public String toString() {
    return "PresignedUpload{correlationId: " + myCorrelationId + ", objectKeysSize: " + myS3ObjectKeys.size() + "}";
  }

  static class SignedUrlInfo {
    @NotNull
    private final String url;
    @Nullable
    private final String digest;
    private final long fetchTiming;

    public SignedUrlInfo(@NotNull String url, @Nullable String digest, long fetchTiming) {
      this.url = url;
      this.digest = digest;
      this.fetchTiming = fetchTiming;
    }

    @NotNull
    public String getUrl() {
      return url;
    }

    @Nullable
    public String getDigest() {
      return digest;
    }

    public long getFetchTiming() {
      return fetchTiming;
    }
  }
}
