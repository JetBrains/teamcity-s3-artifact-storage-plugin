package jetbrains.buildServer.artifacts.s3.publish.presigned.upload;

import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.HttpClientUtil;
import jetbrains.buildServer.artifacts.s3.transport.PresignedUrlDto;
import jetbrains.buildServer.artifacts.s3.transport.PresignedUrlListRequestDto;
import jetbrains.buildServer.artifacts.s3.transport.PresignedUrlListResponseDto;
import jetbrains.buildServer.artifacts.s3.transport.PresignedUrlRequestSerializer;
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

import static jetbrains.buildServer.artifacts.s3.S3Constants.ARTEFACTS_S3_UPLOAD_PRESIGN_URLS_HTML;
import static jetbrains.buildServer.artifacts.s3.transport.PresignedUrlRequestSerializer.*;

public class TeamCityServerPresignedUrlsProviderClient implements PresignedUrlsProviderClient {
  @NotNull
  private static final Logger LOGGER = Logger.getInstance(TeamCityServerPresignedUrlsProviderClient.class);
  @NotNull
  private final String myPresignedUrlsPostUrl;
  @NotNull
  private final HttpClient myTeamCityClient;

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
    try {
      final PostMethod post = postTemplate();
      post.setRequestEntity(s3ObjectKeysRequestEntity(objectKeys));
      final String responseBody = HttpClientUtil.executeReleasingConnectionAndReadResponseBody(myTeamCityClient, post);
      return deserializeResponseV1(responseBody).presignedUrls;
    } catch (HttpClientUtil.HttpErrorCodeException | IOException e) {
      LOGGER.warnAndDebugDetails("Failed resolving S3 pre-signed URL, got exception " + e.getMessage(), e);
      throw new FetchFailedException(e);
    }
  }

  @Override
  @NotNull
  public PresignedUrlDto getMultipartPresignedUrl(@NotNull final String objectKey, final int nParts) {
    try {
      final PostMethod post = postTemplate();
      post.setRequestEntity(multipartRequestEntity(objectKey, nParts));
      final String responseBody = HttpClientUtil.executeReleasingConnectionAndReadResponseBody(myTeamCityClient, post);
      final PresignedUrlListResponseDto presignedUrlListResponseDto = deserializeResponseV2(responseBody);
      return presignedUrlListResponseDto.presignedUrls
        .stream()
        .filter(presignedUrlDto -> Objects.equals(presignedUrlDto.objectKey, objectKey))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Response from s3 urls provider does not contain required object " + objectKey));
    } catch (Exception e) {
      throw new FetchFailedException(e);
    }
  }

  @Override
  public void completeMultipartUpload(@NotNull final S3PresignedUpload upload, @NotNull final String uploadId) {
    finishMultipartUpload(upload, uploadId, true);
  }

  @Override
  public void abortMultipartUpload(@NotNull final S3PresignedUpload upload, @NotNull final String uploadId) {
    finishMultipartUpload(upload, uploadId, false);
  }

  @Override
  public void startMultipartUpload(@NotNull String objectKey, @NotNull String uploadId) {
    // do nothing
  }

  private void finishMultipartUpload(@NotNull final S3PresignedUpload upload, @NotNull final String uploadId, final boolean isSuccessful) {
    LOGGER.debug(() -> "Multipart upload " + upload + " signaling " + (isSuccessful ? "success" : "failure") + " started");
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
  private StringRequestEntity s3ObjectKeysRequestEntity(@NotNull Collection<String> s3ObjectKeys) {
    try {
      return requestEntity(PresignedUrlRequestSerializer.serializeRequestV1(s3ObjectKeys));
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
    HttpClientUtil.shutdown(myTeamCityClient);
  }
}
