package jetbrains.buildServer.artifacts.s3.publish.presigned.upload;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UnsupportedEncodingException;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.net.ssl.SSLException;
import jetbrains.buildServer.artifacts.ArtifactTransportAdditionalHeadersProvider;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.publish.errors.CompositeHttpRequestErrorHandler;
import jetbrains.buildServer.artifacts.s3.publish.errors.HttpResponseErrorHandler;
import jetbrains.buildServer.artifacts.s3.publish.errors.S3ServerResponseErrorHandler;
import jetbrains.buildServer.artifacts.s3.publish.errors.TeamCityPresignedUrlsProviderErrorHandler;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.HttpClientUtil;
import jetbrains.buildServer.artifacts.s3.transport.*;
import jetbrains.buildServer.http.HttpUserAgent;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.ExceptionUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.amazon.retry.Retrier;
import jetbrains.buildServer.util.amazon.retry.impl.AbortingListener;
import jetbrains.buildServer.util.amazon.retry.impl.ExponentialDelayListener;
import jetbrains.buildServer.util.amazon.retry.impl.LoggingRetrierListener;
import jetbrains.buildServer.xmlrpc.NodeIdHolder;
import jetbrains.buildServer.xmlrpc.XmlRpcConstants;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_ARTIFACT_KEYS_HEADER_MAX_NUMBER;
import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_ENABLE_MULTIPART_COMPLETION_RETRY;
import static jetbrains.buildServer.artifacts.s3.transport.PresignedUrlRequestSerializer.*;

public class TeamCityServerPresignedUrlsProviderClient implements PresignedUrlsProviderClient {
  @NotNull
  private static final Logger LOGGER = Logger.getInstance(TeamCityServerPresignedUrlsProviderClient.class);
  public static final int DEFAULT_ARTIFACT_KEYS_HEADER_MAX_NUMBER = 10;
  @NotNull
  private final String myPresignedUrlsPostUrl;
  @NotNull
  private final TeamCityConnectionConfiguration myTeamCityConnectionConfiguration;
  @NotNull
  private final AtomicReference<CloseableHttpClient> myTeamCityClient;
  @NotNull
  private final BasicCookieStore myCookieStore;
  @NotNull
  private final Collection<ArtifactTransportAdditionalHeadersProvider> myAdditionalHeadersProviders;
  @NotNull
  private final AtomicBoolean myShutdown = new AtomicBoolean(false);
  @NotNull
  private final HttpResponseErrorHandler myErrorHandler = new CompositeHttpRequestErrorHandler(new S3ServerResponseErrorHandler(), new TeamCityPresignedUrlsProviderErrorHandler());

  private final NodeIdHolder myNodeIdHolder;
  private final String myServerUrl;
  @NotNull
  private final Retrier myRetrier;

  public TeamCityServerPresignedUrlsProviderClient(@NotNull final TeamCityConnectionConfiguration teamCityConnectionConfiguration,
                                                   @NotNull final Collection<ArtifactTransportAdditionalHeadersProvider> additionalHeadersProviders) {
    myPresignedUrlsPostUrl = teamCityConnectionConfiguration.getTeamCityUrl() + "/httpAuth/" + StringUtil.removeLeadingSlash(teamCityConnectionConfiguration.getUrlsProviderPath());
    myTeamCityConnectionConfiguration = teamCityConnectionConfiguration;
    myCookieStore = new BasicCookieStore();
    myTeamCityClient = new AtomicReference<>(createClient(myTeamCityConnectionConfiguration));
    myAdditionalHeadersProviders = additionalHeadersProviders;
    myNodeIdHolder = teamCityConnectionConfiguration.getNodeIdHolder();
    myServerUrl = teamCityConnectionConfiguration.getTeamCityUrl();

    myRetrier = Retrier.withRetries(myTeamCityConnectionConfiguration.getRetriesNum())
                       .registerListener(new LoggingRetrierListener(LOGGER))
                       .registerListener(new AbortingListener(SSLException.class, UnknownHostException.class, SocketException.class, InterruptedIOException.class,
                                                              InterruptedException.class))
                       .registerListener(new ExponentialDelayListener(myTeamCityConnectionConfiguration.getRetryDelay()));
  }

  @NotNull
  private CloseableHttpClient createClient(@NotNull final TeamCityConnectionConfiguration config) {
    try {
      final URI url = new URI(myPresignedUrlsPostUrl);
      final AuthScope scope = new AuthScope(url.getHost(), url.getPort());
      final CredentialsProvider provider = new BasicCredentialsProvider();
      provider.setCredentials(scope, config.getCredentials());
      int connectionTimeout = config.getConnectionTimeout() * 1000;
      final RequestConfig requestConfig = RequestConfig.custom()
        .setConnectionRequestTimeout(connectionTimeout)
        .setSocketTimeout(connectionTimeout)
        .build();

      return HttpClients.custom()
                        .setDefaultCredentialsProvider(provider)
                        .setDefaultRequestConfig(requestConfig)
                        .setMaxConnPerRoute(config.getNThreads())
                        .setDefaultCookieStore(myCookieStore)
                        .build();
    } catch (URISyntaxException e) {
      LOGGER.warnAndDebugDetails("Malformed url to TeamCity server", e);
      throw new MisconfigurationException(e);
    }
  }

  @Override
  @NotNull
  public Collection<PresignedUrlDto> getRegularPresignedUrls(@NotNull final List<String> objectKeys, @NotNull Map<String, String> digests) {
    validateClient();
    try {
      final HttpPost post = postTemplate();
      post.setEntity(s3ObjectKeysRequestEntity(objectKeys, digests));
      int maxHeaders = TeamCityProperties.getInteger(S3_ARTIFACT_KEYS_HEADER_MAX_NUMBER, DEFAULT_ARTIFACT_KEYS_HEADER_MAX_NUMBER);
      for (int i = 0; i < Math.min(maxHeaders, objectKeys.size()); i++) {
        post.addHeader(S3Constants.S3_ARTIFACT_KEYS_HEADER_NAME, objectKeys.get(i));
      }

      setNodeIdCookie();

      final HttpResponse response = HttpClientUtil.executeAndReleaseConnection(myTeamCityClient.get(), post, myErrorHandler).get();
      final String responseBody = EntityUtils.toString(response.getEntity());

      return deserializeResponseV2(responseBody).getPresignedUrls();
    } catch (HttpClientUtil.HttpErrorCodeException | IOException | ExecutionException | InterruptedException e) {
      LOGGER.warnAndDebugDetails("Failed resolving S3 pre-signed URL, got exception " + e.getMessage(), e);
      throw new FetchFailedException(e);
    }
  }

  private void setNodeIdCookie() {
    // set current node id cookie to instruct proxy server where this request should be landed
    final String nodeId = myNodeIdHolder.getOwnerNodeId();
    if (nodeId == null) return;

    try {
      final URI uri = new URI(myServerUrl);
      final BasicClientCookie cookie = getCookie(uri);
      myCookieStore.addCookie(cookie);
    } catch (URISyntaxException e) {
      Loggers.AGENT.warnAndDebugDetails("Failed to create java.net.URL object from: " + myServerUrl + ", cookie: " + XmlRpcConstants.NODE_ID_COOKIE + " cannot be set", e);
    }
  }

  @NotNull
  private BasicClientCookie getCookie(URI uri) {
    final BasicClientCookie cookie = new BasicClientCookie(XmlRpcConstants.NODE_ID_COOKIE, myNodeIdHolder.getOwnerNodeId());
    cookie.setDomain(uri.getHost());
    cookie.setPath("/");
    cookie.setExpiryDate(new Date(System.currentTimeMillis() + myNodeIdHolder.getExpirationTime() * 1000L));
    cookie.setSecure(false);
    return cookie;
  }

  @NotNull
  public PresignedUrlDto getUrl(@NotNull final String objectKey, @Nullable String digest, @Nullable Long ttl) {
    validateClient();
    try {
      return fetchPresignedUrlDto(objectKey, s3ObjectKeyRequestEntity(objectKey, digest, ttl));
    } catch (HttpClientUtil.HttpErrorCodeException | IOException | ExecutionException | InterruptedException e) {
      LOGGER.warnAndDebugDetails("Failed resolving S3 pre-signed URL, got exception " + e.getMessage(), e);
      throw new FetchFailedException(e);
    }
  }

  @Override
  @NotNull
  public PresignedUrlDto getMultipartPresignedUrl(@NotNull final String objectKey, @NotNull final List<String> digests, @Nullable String uploadId, @Nullable Long ttl) {
    validateClient();
    try {
      return fetchPresignedUrlDto(objectKey, multipartRequestEntity(objectKey, digests, uploadId, ttl));
    } catch (Exception e) {
      throw new FetchFailedException(e);
    }
  }

  @NotNull
  private PresignedUrlDto fetchPresignedUrlDto(@NotNull final String objectKey, @NotNull final StringEntity requestEntity)
    throws IOException, ExecutionException, InterruptedException {
    final HttpPost post = postTemplate();
    post.setEntity(requestEntity);
    post.setHeader("Content-Type", "application/xml; charset=" + StandardCharsets.UTF_8.name());
    post.setHeader(S3Constants.S3_ARTIFACT_KEYS_HEADER_NAME, objectKey);
    final HttpResponse response = HttpClientUtil.executeAndReleaseConnection(myTeamCityClient.get(), post, myErrorHandler).get();
    final String responseBody = EntityUtils.toString(response.getEntity());
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

    final boolean isRetryEnabled = TeamCityProperties.getBoolean(S3_ENABLE_MULTIPART_COMPLETION_RETRY);
    if (isRetryEnabled) {

      myRetrier.execute(() -> {
        recreateClient();
        finishMultipartUploadImpl(objectKey, etags, uploadId, isSuccessful);
      });
    } else {
      try {
        finishMultipartUploadImpl(objectKey, etags, uploadId, isSuccessful);
      } catch (IOException e) {
        ExceptionUtil.rethrowAsRuntimeException(e);
      }
    }
  }

  private void recreateClient() {
    final CloseableHttpClient oldClient = myTeamCityClient.getAndUpdate(old -> createClient(myTeamCityConnectionConfiguration));
    HttpClientUtil.shutdown(oldClient);
  }

  private void finishMultipartUploadImpl(@NotNull String objectKey, @Nullable List<String> etags, @NotNull String uploadId, boolean isSuccessful) throws IOException {
    final HttpPost post = postTemplate();
    final MultipartEntity multipartEntity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);
    post.setEntity(multipartEntity);

    multipartEntity.addPart(OBJECT_KEY, new StringBody(objectKey));
    multipartEntity.addPart(OBJECT_KEY + "_BASE64", new StringBody(Base64.getEncoder().encodeToString(objectKey.getBytes(StandardCharsets.UTF_8))));
    multipartEntity.addPart(FINISH_UPLOAD, new StringBody(uploadId));
    multipartEntity.addPart(UPLOAD_SUCCESSFUL, new StringBody(String.valueOf(isSuccessful)));
    if (isSuccessful && etags != null) {
      for (String etag : etags) {
        multipartEntity.addPart(ETAGS, new StringBody(etag));
      }
    }
    try {
      HttpClientUtil.executeAndReleaseConnection(myTeamCityClient.get(), post, myErrorHandler);
      LOGGER.debug(() -> "Multipart upload " + uploadId + " signaling " + (isSuccessful ? "success" : "failure") + " finished");
    } catch (Exception e) {
      LOGGER.warnAndDebugDetails("Multipart upload " + uploadId + " signaling " + (isSuccessful ? "success" : "failure") + " failed: " + e.getMessage(), e);
      throw e;
    }
  }


  @NotNull
  private StringEntity multipartRequestEntity(@NotNull final String s3ObjectKey, @NotNull final List<String> digests, @Nullable String uploadId, @Nullable Long ttl) {
    try {
      return requestEntity(PresignedUrlRequestSerializer.serializeRequestV2(PresignedUrlListRequestDto.forObjectKeyMultipart(s3ObjectKey, uploadId, digests, ttl)));
    } catch (UnsupportedEncodingException e) {
      LOGGER.warnAndDebugDetails("Unsupported encoding", e);
      throw new MisconfigurationException(e);
    }
  }

  @NotNull
  private StringEntity requestEntity(@NotNull String xml) throws UnsupportedEncodingException {
    return new StringEntity(xml, ContentType.create(ContentType.APPLICATION_XML.getMimeType(), StandardCharsets.UTF_8));
  }

  @NotNull
  private StringEntity s3ObjectKeysRequestEntity(@NotNull Collection<String> s3ObjectKeys, Map<String, String> digests) {
    try {
      final List<Pair<String, String>> keysWithDigests = s3ObjectKeys.stream().map(k -> Pair.create(k, digests.get(k))).collect(Collectors.toList());
      return requestEntity(PresignedUrlRequestSerializer.serializeRequestV2(PresignedUrlListRequestDto.forObjectKeysWithDigests(keysWithDigests)));
    } catch (UnsupportedEncodingException e) {
      LOGGER.warnAndDebugDetails("Unsupported encoding", e);
      throw new MisconfigurationException(e);
    }
  }

  @NotNull
  private StringEntity s3ObjectKeyRequestEntity(@NotNull String objectKey, @Nullable String digest, @Nullable Long ttl) {
    try {
      return requestEntity(PresignedUrlRequestSerializer.serializeRequestV2(PresignedUrlListRequestDto.forObjectKeyWithDigest(objectKey, digest, ttl)));
    } catch (UnsupportedEncodingException e) {
      LOGGER.warnAndDebugDetails("Unsupported encoding", e);
      throw new MisconfigurationException(e);
    }
  }

  @NotNull
  private HttpPost postTemplate() {
    final HttpPost post = new HttpPost(myPresignedUrlsPostUrl);
    post.addHeader(HttpHeaders.USER_AGENT, HttpUserAgent.getUserAgent());
    post.setHeader("Accept", "application/xml");
    post.setHeader("Accept-Charset", StandardCharsets.UTF_8.name());
    addAdditionalHeaders(post);
    return post;
  }

  @Override
  public void close() {
    myShutdown.set(true);
    HttpClientUtil.shutdown(myTeamCityClient.get());
  }

  private void validateClient() {
    if (myShutdown.get()) {
      LOGGER.warn("TeamCity presigned urls provider client already shut down");
      throw new ClientAlreadyShutdownException("TeamCity presigned urls provider client already shut down");
    }
  }

  private void addAdditionalHeaders(HttpPost request) {
    HashMap<String, String> headerToProviderMap = new HashMap<>();
    ArtifactTransportAdditionalHeadersProvider.Configuration configuration = () -> request.getMethod();
    for (ArtifactTransportAdditionalHeadersProvider extension : myAdditionalHeadersProviders) {
      List<ArtifactTransportAdditionalHeadersProvider.Header> headers = extension.getHeaders(configuration);
      String extensionName = extension.getClass().getName();
      for (ArtifactTransportAdditionalHeadersProvider.Header header : headers) {
        String existingExtensionsName = headerToProviderMap.get(header.getName().toUpperCase());
        if (existingExtensionsName == null) {
          request.addHeader(header.getName(), header.getValue());
          headerToProviderMap.put(header.getName().toUpperCase(), extensionName);
        } else {
          String headerName = header.getName();
          String message = String.format("Multiple extensions(%s, %s) provide the same additional header '%s'", existingExtensionsName, extensionName, headerName);
          LOGGER.warn(message);
        }
      }
    }
  }

  public static class ClientAlreadyShutdownException extends IllegalStateException {
    public ClientAlreadyShutdownException(@NotNull final String message) {
      super(message);
    }
  }
}
