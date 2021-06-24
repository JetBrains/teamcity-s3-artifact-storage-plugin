package jetbrains.buildServer.artifacts.s3.publish.presigned;

import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.HttpClientUtil;
import jetbrains.buildServer.artifacts.s3.transport.PresignedUrlDto;
import jetbrains.buildServer.artifacts.s3.transport.PresignedUrlListRequestDto;
import jetbrains.buildServer.artifacts.s3.transport.PresignedUrlListResponseDto;
import jetbrains.buildServer.artifacts.s3.transport.PresignedUrlRequestSerializer;
import jetbrains.buildServer.http.HttpUserAgent;
import jetbrains.buildServer.http.HttpUtil;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.ExceptionUtil;
import jetbrains.buildServer.util.NamedThreadFactory;
import jetbrains.buildServer.util.UptodateValue;
import jetbrains.buildServer.util.amazon.S3Util;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.artifacts.s3.S3Constants.ARTEFACTS_S3_UPLOAD_PRESIGN_URLS_HTML;
import static jetbrains.buildServer.artifacts.s3.transport.PresignedUrlRequestSerializer.*;

public class S3SignedUploadManager implements AutoCloseable {
  @NotNull
  private static final Logger LOGGER = Logger.getInstance(S3SignedUploadManager.class);
  @NotNull
  private final HttpClient myTeamCityClient;
  @NotNull
  private final String myPresignedUrlsPostUrl;
  @NotNull
  private final List<String> myS3ObjectKeys;
  @NotNull
  private final UptodateValue<Map<String, String>> myCache;
  private final int myMaxUrlChunkSize;
  @NotNull
  private final Lock myFetchLock = new ReentrantLock();
  @NotNull
  private final Map<String, String> myMultipartUploadIds = new ConcurrentHashMap<>();
  @NotNull
  private final String myCorrelationId = UUID.randomUUID().toString();

  public S3SignedUploadManager(@NotNull final TeamCityConnectionConfiguration tcConfig,
                               @NotNull final S3Util.S3AdvancedConfiguration s3Config,
                               @NotNull final Collection<String> s3ObjectKeys) {
    myPresignedUrlsPostUrl = tcConfig.getTeamCityUrl() + "/httpAuth" + ARTEFACTS_S3_UPLOAD_PRESIGN_URLS_HTML;
    myTeamCityClient = createClient(tcConfig);
    myS3ObjectKeys = new ArrayList<>(s3ObjectKeys);
    myMaxUrlChunkSize = s3Config.getPresignedUrlMaxChunkSize();
    myCache = new UptodateValue<>(this::fetchUploadUrlsFromServer, s3Config.getUrlTtlSeconds() * 1000L);
  }


  @NotNull
  public String getUrl(@NotNull final String s3ObjectKey) {
    return myCache.getValue().get(s3ObjectKey);
  }


  @NotNull
  private Map<String, String> fetchUploadUrlsFromServer() {
    try {
      return NamedThreadFactory.executeWithNewThreadName("Fetching presigned urls for " + this, () -> {
        LOGGER.debug(() -> "Fetching presigned urls for manager " + this + " started");
        myFetchLock.lock();
        try {
          return CollectionsUtil.split(myS3ObjectKeys, (myS3ObjectKeys.size() / myMaxUrlChunkSize) + 1)
                                .stream()
                                .peek(keys -> LOGGER.debug(() -> "Fetching chunk " + keys + " of size " + keys.size() + " of total " + myS3ObjectKeys.size() + " started"))
                                .map(this::fetchChunk)
                                .peek(keys -> LOGGER.debug(() -> "Fetching chunk " + keys + " of size " + keys.size() + " of total " + myS3ObjectKeys.size() + " finished"))
                                .flatMap(map -> map.entrySet().stream())
                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        } finally {
          myFetchLock.unlock();
          LOGGER.debug(() -> "Fetching presigned urls for manager " + this + " finished");
        }
      });
    } catch (Exception e) {
      LOGGER.info("Got exception while fetching presigned urls", e);
      ExceptionUtil.rethrowAsRuntimeException(e);
      return null;
    }
  }

  @NotNull
  private Map<String, String> fetchChunk(@NotNull final List<String> objectKeys) {
    try {
      final PostMethod post = postTemplate();
      post.setRequestEntity(requestEntity(objectKeys));
      final String responseBody = HttpClientUtil.executeReleasingConnectionAndReadResponseBody(myTeamCityClient, post);
      return deserializeResponseV1(responseBody).presignedUrls
        .stream()
        .collect(Collectors.toMap(presignedUrlDto -> presignedUrlDto.objectKey,
                                  presignedUrlDto -> presignedUrlDto.presignedUrlParts
                                    .stream()
                                    .map(url -> url.url)
                                    .findFirst()
                                    .orElseThrow(() -> new IllegalArgumentException("No urls provided in response"))));
    } catch (HttpClientUtil.HttpErrorCodeException | IOException e) {
      LOGGER.warnAndDebugDetails("Failed resolving S3 pre-signed URL, got exception " + e.getMessage(), e);
      throw new FetchFailedException(e);
    }
  }

  @NotNull
  private HttpClient createClient(@NotNull final TeamCityConnectionConfiguration config) {
    try {
      final HttpClient httpClient = HttpUtil.createHttpClient(config.getConnectionTimeout(), new URL(myPresignedUrlsPostUrl), config.getCredentials());
      final HttpConnectionManager httpConnectionManager = HttpClientUtil.createConnectionManager(config.getConnectionTimeout(), config.getNThreads());
      httpClient.setHttpConnectionManager(httpConnectionManager);
      return httpClient;
    } catch (MalformedURLException e) {
      LOGGER.warnAndDebugDetails("Malformed url to TeamCity server", e);
      throw new MisconfigurationException(e);
    }
  }

  @NotNull
  private PostMethod postTemplate() {
    final PostMethod post = new PostMethod(myPresignedUrlsPostUrl);
    post.addRequestHeader(HttpHeaders.USER_AGENT, HttpUserAgent.getUserAgent());
    post.setDoAuthentication(true);
    return post;
  }

  @NotNull
  private StringRequestEntity requestEntity(@NotNull Collection<String> s3ObjectKeys) {
    try {
      return requestEntity(PresignedUrlRequestSerializer.serializeRequestV1(s3ObjectKeys));
    } catch (UnsupportedEncodingException e) {
      LOGGER.warnAndDebugDetails("Unsupported encoding", e);
      throw new MisconfigurationException(e);
    }
  }

  @NotNull
  private StringRequestEntity multipartRequestEntity(@NotNull final String s3ObjectKey, final int nParts) {
    try {
      return requestEntity(PresignedUrlRequestSerializer.serializeRequestV2(PresignedUrlListRequestDto.forObjectKeyMultipart(s3ObjectKey, nParts)));
    } catch (UnsupportedEncodingException e) {
      LOGGER.warnAndDebugDetails("Unsupported encoding", e);
      throw new MisconfigurationException(e);
    }
  }

  @NotNull
  private StringRequestEntity requestEntity(@NotNull String xml) throws UnsupportedEncodingException {
    return new StringRequestEntity(xml, ContentType.APPLICATION_XML.getMimeType(), StandardCharsets.UTF_8.name());
  }

  @Override
  public void close() {
    HttpClientUtil.shutdown(myTeamCityClient);
  }

  @NotNull
  public PresignedUrlDto getMultipartUploadUrls(@NotNull final String objectKey, final int nParts) throws IOException {
    final PostMethod post = postTemplate();
    post.setRequestEntity(multipartRequestEntity(objectKey, nParts));
    final String responseBody = HttpClientUtil.executeReleasingConnectionAndReadResponseBody(myTeamCityClient, post);
    final PresignedUrlListResponseDto presignedUrlListResponseDto = deserializeResponseV2(responseBody);
    final PresignedUrlDto presignedUrl = presignedUrlListResponseDto.presignedUrls
      .stream()
      .filter(presignedUrlDto -> Objects.equals(presignedUrlDto.objectKey, objectKey))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Response from server does not contain required object " + objectKey));
    myMultipartUploadIds.put(presignedUrl.objectKey, presignedUrl.uploadId);
    return presignedUrl;
  }

  public void onUploadSuccess(@NotNull final S3PresignedUpload upload) {
    if (myMultipartUploadIds.containsKey(upload.getObjectKey()) && upload.isMultipartUpload()) {
      sendUploadFinished(upload, true);
      myS3ObjectKeys.remove(upload.getObjectKey());
    }
  }

  public void onUploadFailed(@NotNull final S3PresignedUpload upload) {
    if (myMultipartUploadIds.containsKey(upload.getObjectKey()) && upload.isMultipartUpload()) {
      sendUploadFinished(upload, false);
    }
  }

  private void sendUploadFinished(@NotNull final S3PresignedUpload upload, final boolean isSuccessful) {
    LOGGER.debug(() -> "Multipart upload " + upload + " signaling " + (isSuccessful ? "success" : "failure") + " started");
    final String uploadId = myMultipartUploadIds.get(upload.getObjectKey());
    if (uploadId != null) {
      final PostMethod post = postTemplate();
      post.setParameter(OBJECT_KEY, upload.getObjectKey());
      post.setParameter(FINISH_UPLOAD, uploadId);
      post.setParameter(UPLOAD_SUCCESSFUL, String.valueOf(isSuccessful));
      if (isSuccessful) {
        upload.getEtags().forEach(etag -> post.addParameter(ETAGS, etag));
      }
      try {
        HttpClientUtil.executeReleasingConnectionAndReadResponseBody(myTeamCityClient, post);
        LOGGER.debug(() -> "Multipart upload " + upload + " signaling " + (isSuccessful ? "success" : "failure") + " finished");
      } catch (Exception e) {
        LOGGER.warnAndDebugDetails("Multipart upload " + upload + " signaling " + (isSuccessful ? "success" : "failure") + " failed: " + e.getMessage(), e);
        ExceptionUtil.rethrowAsRuntimeException(e);
      }
      myMultipartUploadIds.remove(upload.getObjectKey());
    }
  }

  @Override
  public String toString() {
    return "PresignedUpload{correlationId: " + myCorrelationId + ", objectKeysSize: " + myS3ObjectKeys.size() + "}";
  }

  static class MisconfigurationException extends RuntimeException {
    public MisconfigurationException(@NotNull final Throwable cause) {
      super(cause);
    }
  }

  static class FetchFailedException extends RuntimeException {
    public FetchFailedException(@NotNull final Throwable cause) {
      super(cause);
    }
  }
}
