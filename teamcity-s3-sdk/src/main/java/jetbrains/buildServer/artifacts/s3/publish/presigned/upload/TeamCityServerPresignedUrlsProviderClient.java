package jetbrains.buildServer.artifacts.s3.publish.presigned.upload;

import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.HttpClientUtil;
import jetbrains.buildServer.artifacts.s3.transport.*;
import jetbrains.buildServer.http.HttpUserAgent;
import jetbrains.buildServer.http.HttpUtil;
import jetbrains.buildServer.util.ExceptionUtil;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.artifacts.s3.S3Constants.ARTEFACTS_S3_UPLOAD_PRESIGN_URLS_HTML;
import static jetbrains.buildServer.artifacts.s3.transport.PresignedUrlRequestSerializer.*;

public class TeamCityServerPresignedUrlsProviderClient implements PresignedUrlsProviderClient {
  @NotNull
  private static final Logger LOGGER = Logger.getInstance(TeamCityServerPresignedUrlsProviderClient.class);
  @NotNull
  private final String myPresignedUrlsPostUrl;
  @NotNull
  private final HttpClient myTeamCityClient;
  @NotNull
  private final AtomicBoolean myShutdown = new AtomicBoolean(false);

  public TeamCityServerPresignedUrlsProviderClient(@NotNull final TeamCityConnectionConfiguration teamCityConnectionConfiguration) {
    myPresignedUrlsPostUrl = teamCityConnectionConfiguration.getTeamCityUrl() + "/httpAuth" + ARTEFACTS_S3_UPLOAD_PRESIGN_URLS_HTML;
    myTeamCityClient = createClient(teamCityConnectionConfiguration);
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

  @Override
  @NotNull
  public Collection<PresignedUrlDto> getRegularPresignedUrls(@NotNull final List<String> objectKeys) {
    validateClient();
    try {
      final PostMethod post = postTemplate();
      post.setRequestEntity(s3ObjectKeysRequestEntity(objectKeys, null));
      final String responseBody = HttpClientUtil.executeReleasingConnectionAndReadResponseBody(myTeamCityClient, post);
      return deserializeResponseV1(responseBody).getPresignedUrls();
    } catch (HttpClientUtil.HttpErrorCodeException | IOException e) {
      LOGGER.warnAndDebugDetails("Failed resolving S3 pre-signed URL, got exception " + e.getMessage(), e);
      throw new FetchFailedException(e);
    }
  }

  @NotNull
  public PresignedUrlDto getUrl(@NotNull final String objectKey, @NotNull final String httpMethod) {
    validateClient();
    try {
      return fetchPresignedUrlDto(objectKey, s3ObjectKeysRequestEntity(Collections.singletonList(objectKey), httpMethod));
    } catch (HttpClientUtil.HttpErrorCodeException | IOException e) {
      LOGGER.warnAndDebugDetails("Failed resolving S3 pre-signed URL, got exception " + e.getMessage(), e);
      throw new FetchFailedException(e);
    }
  }

  @Override
  @NotNull
  public PresignedUrlDto getMultipartPresignedUrl(@NotNull final String objectKey, final int nParts) {
    validateClient();
    try {
      return fetchPresignedUrlDto(objectKey, multipartRequestEntity(objectKey, nParts));
    } catch (Exception e) {
      throw new FetchFailedException(e);
    }
  }

  @NotNull
  private PresignedUrlDto fetchPresignedUrlDto(@NotNull final String objectKey, @NotNull final StringRequestEntity requestEntity) throws IOException {
    final PostMethod post = postTemplate();
    post.setRequestEntity(requestEntity);
    final String responseBody = HttpClientUtil.executeReleasingConnectionAndReadResponseBody(myTeamCityClient, post);
    final PresignedUrlListResponseDto presignedUrlListResponseDto = deserializeResponseV2(responseBody);
    return presignedUrlListResponseDto.getPresignedUrls()
                                      .stream()
                                      .filter(presignedUrlDto -> Objects.equals(presignedUrlDto.getObjectKey(), objectKey))
                                      .findFirst()
                                      .orElseThrow(() -> new IllegalArgumentException("Response from s3 urls provider does not contain required object " + objectKey));
  }

  @Override
  public void completeMultipartUpload(@NotNull MultipartUploadCompleteRequestDto multipartUploadComplete) {
    validateClient();
    finishMultipartUpload(Objects.requireNonNull(multipartUploadComplete.getObjectKey()),
                          multipartUploadComplete.getEtags(),
                          Objects.requireNonNull(multipartUploadComplete.getUploadId()),
                          true);
  }

  @Override
  public void abortMultipartUpload(@NotNull final MultipartUploadAbortRequestDto multipartUploadAbort) {
    validateClient();
    finishMultipartUpload(multipartUploadAbort.getObjectKey(), null, multipartUploadAbort.getUploadId(), false);
  }

  @Override
  public void startMultipartUpload(@NotNull final MultipartUploadStartRequestDto multipartUploadStart) {
    // do nothing
  }

  private void finishMultipartUpload(@NotNull final String objectKey, @Nullable final List<String> etags, @NotNull final String uploadId, final boolean isSuccessful) {
    LOGGER.debug(() -> "Multipart upload " + uploadId + " signaling " + (isSuccessful ? "success" : "failure") + " started");
    final PostMethod post = postTemplate();
    post.setParameter(OBJECT_KEY, objectKey);
    post.setParameter(FINISH_UPLOAD, uploadId);
    post.setParameter(UPLOAD_SUCCESSFUL, String.valueOf(isSuccessful));
    if (isSuccessful && etags != null) {
      etags.forEach(etag -> post.addParameter(ETAGS, etag));
    }
    try {
      HttpClientUtil.executeReleasingConnectionAndReadResponseBody(myTeamCityClient, post);
      LOGGER.debug(() -> "Multipart upload " + uploadId + " signaling " + (isSuccessful ? "success" : "failure") + " finished");
    } catch (Exception e) {
      LOGGER.warnAndDebugDetails("Multipart upload " + uploadId + " signaling " + (isSuccessful ? "success" : "failure") + " failed: " + e.getMessage(), e);
      ExceptionUtil.rethrowAsRuntimeException(e);
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

  @NotNull
  private StringRequestEntity s3ObjectKeysRequestEntity(@NotNull Collection<String> s3ObjectKeys, @Nullable String httpMethod) {
    try {
      if (httpMethod == null) {
        return requestEntity(PresignedUrlRequestSerializer.serializeRequestV1(s3ObjectKeys));
      } else {
        return requestEntity(PresignedUrlRequestSerializer.serializeRequestV2(PresignedUrlListRequestDto.forObjectKeysWithMethod(s3ObjectKeys, httpMethod)));
      }
    } catch (UnsupportedEncodingException e) {
      LOGGER.warnAndDebugDetails("Unsupported encoding", e);
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

  @Override
  public void close() {
    myShutdown.set(true);
    HttpClientUtil.shutdown(myTeamCityClient);
  }

  private void validateClient() {
    if (myShutdown.get()) {
      LOGGER.warn("TeamCity presigned urls provider client already shut down");
      throw new ClientAlreadyShutdownException("TeamCity presigned urls provider client already shut down");
    }
  }

  public static class ClientAlreadyShutdownException extends IllegalStateException {
    public ClientAlreadyShutdownException(@NotNull final String message) {
      super(message);
    }
  }
}
