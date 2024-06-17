package jetbrains.buildServer.artifacts.s3.publish.presigned.upload;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
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
import jetbrains.buildServer.util.HTTPRequestBuilder;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.http.EntityProducer;
import jetbrains.buildServer.util.retry.Retrier;
import jetbrains.buildServer.util.retry.impl.AbortingListener;
import jetbrains.buildServer.util.retry.impl.LoggingRetrierListener;
import jetbrains.buildServer.xmlrpc.NodeIdCookie;
import jetbrains.buildServer.xmlrpc.NodeIdHolder;
import jetbrains.buildServer.xmlrpc.XmlRpcConstants;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.StringBody;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_ARTIFACT_KEYS_HEADER_MAX_NUMBER;
import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_ENABLE_MULTIPART_COMPLETION_RETRY;
import static jetbrains.buildServer.artifacts.s3.transport.PresignedUrlRequestSerializer.*;
import static jetbrains.buildServer.util.http.HttpMethod.POST;

public class TeamCityServerPresignedUrlsProviderClient implements PresignedUrlsProviderClient {
  @NotNull
  private static final Logger LOGGER = Logger.getInstance(TeamCityServerPresignedUrlsProviderClient.class);
  public static final int DEFAULT_ARTIFACT_KEYS_HEADER_MAX_NUMBER = 10;
  @NotNull
  private final String myPresignedUrlsPostUrl;
  @NotNull
  private final TeamCityConnectionConfiguration myTeamCityConnectionConfiguration;
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
    myAdditionalHeadersProviders = additionalHeadersProviders;
    myNodeIdHolder = teamCityConnectionConfiguration.getNodeIdHolder();
    myServerUrl = teamCityConnectionConfiguration.getTeamCityUrl();

    myRetrier = Retrier.withRetries(myTeamCityConnectionConfiguration.getRetriesNum(), Retrier.DelayStrategy.linearBackOff(myTeamCityConnectionConfiguration.getRetryDelay()))
                       .registerListener(new LoggingRetrierListener(LOGGER))
                       .registerListener(new AbortingListener(ExecutionException.class, SSLException.class, UnknownHostException.class, SocketException.class, InterruptedIOException.class, InterruptedException.class, IOException.class));
  }

  @Override
  @NotNull
  public Collection<PresignedUrlDto> getRegularPresignedUrls(@NotNull final List<String> objectKeys, @NotNull Map<String, String> digests) {
    validateClient();
    try {
      final HTTPRequestBuilder post = postTemplate()
        .withData(s3ObjectKeysRequestEntity(objectKeys, digests));

      int maxHeaders = TeamCityProperties.getInteger(S3_ARTIFACT_KEYS_HEADER_MAX_NUMBER, DEFAULT_ARTIFACT_KEYS_HEADER_MAX_NUMBER);
      for (int i = 0; i < Math.min(maxHeaders, objectKeys.size()); i++) {
        post.withHeader(S3Constants.S3_ARTIFACT_KEYS_HEADER_NAME, objectKeys.get(i));
      }

      setNodeIdCookie(post);

      final String responseBody = HttpClientUtil.executeAndReleaseConnection(post, myErrorHandler).get().getResponse();
      if (StringUtil.isEmpty(responseBody)) {
        throw new FetchFailedException("Response returned with empty body");
      }

      return deserializeResponseV2(responseBody).getPresignedUrls();
    } catch (HttpClientUtil.HttpErrorCodeException | URISyntaxException | ExecutionException e) {
      LOGGER.warnAndDebugDetails("Failed resolving S3 pre-signed URL from " + myServerUrl + ", got exception " + e.getMessage(), e);
      throw new FetchFailedException(e);
    } catch (InterruptedException e) {
      throw new FetchFailedException("Resolving S3 pre-signed URL was interrupted");
    }
  }

  private void setNodeIdCookie(HTTPRequestBuilder post) {
    // set current node id cookie to instruct proxy server where this request should be landed
    final String nodeId = myNodeIdHolder.getOwnerNodeId();
    if (nodeId == null) return;

    try {
      post.withCookie(NodeIdCookie.getCookie(myNodeIdHolder, new URL(myServerUrl)));
    } catch (MalformedURLException e) {
      Loggers.AGENT.warnAndDebugDetails("Failed to create java.net.URL object from: " + myServerUrl + ", cookie: " + XmlRpcConstants.NODE_ID_COOKIE + " cannot be set", e);
    }
  }

  @NotNull
  public PresignedUrlDto getUrl(@NotNull final String objectKey, @Nullable String digest, @Nullable Long ttl) {
    validateClient();
    try {
      return fetchPresignedUrlDto(objectKey, s3ObjectKeyRequestEntity(objectKey, digest, ttl));
    } catch (IOException | URISyntaxException | RuntimeException | ExecutionException e) {
      LOGGER.warnAndDebugDetails("Failed resolving S3 pre-signed URL, got exception " + e.getMessage(), e);
      throw new FetchFailedException(e);
    } catch (InterruptedException e) {
      throw new FetchFailedException("Resolving S3 pre-signed URL was interrupted");
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
  private PresignedUrlDto fetchPresignedUrlDto(@NotNull final String objectKey, @NotNull final EntityProducer requestEntity)
    throws IOException, URISyntaxException, ExecutionException, InterruptedException {
    final HTTPRequestBuilder post = postTemplate()
      .withData(requestEntity)
      .withHeader("Content-Type", "application/xml; charset=" + StandardCharsets.UTF_8.name())
      .withHeader(S3Constants.S3_ARTIFACT_KEYS_HEADER_NAME, objectKey);

    final String response = HttpClientUtil.executeAndReleaseConnection(post, myErrorHandler).get().getResponse();
    if (StringUtil.isEmpty(response)) {
      throw new IllegalStateException("Response body is empty");
    }

    final PresignedUrlListResponseDto presignedUrlListResponseDto = deserializeResponseV2(response);
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
        finishMultipartUploadImpl(objectKey, etags, uploadId, isSuccessful);
      });
    } else {
      try {
        finishMultipartUploadImpl(objectKey, etags, uploadId, isSuccessful);
      } catch (IOException | URISyntaxException | ExecutionException | InterruptedException e) {
        ExceptionUtil.rethrowAsRuntimeException(e);
      }
    }
  }

  private void finishMultipartUploadImpl(@NotNull String objectKey, @Nullable List<String> etags, @NotNull String uploadId, boolean isSuccessful)
    throws IOException, URISyntaxException, ExecutionException, InterruptedException {
    final HTTPRequestBuilder post = postTemplate();
    final List<Pair<String, String>> parameters = getParameters(objectKey, etags, uploadId, isSuccessful);
    post
      .withData(new EntityProducer() {
        @Override
        public HttpEntity entity4() {
          try {
            final MultipartEntity multipartEntity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);
            for (Pair<String, String> entry : parameters) {
              multipartEntity.addPart(entry.getFirst(), new StringBody(entry.getSecond()));
            }
            return multipartEntity;
          } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
          }
        }

        @Override
        public RequestEntity entity3() {
          Part[] parts = parameters.stream()
                                   .map(entry -> new StringPart(entry.getFirst(), entry.getSecond()))
                                   .collect(Collectors.toList()).toArray(new Part[0]);
          return new MultipartRequestEntity(parts, new HttpMethodParams());
        }
      });

    try {
      HttpClientUtil.executeAndReleaseConnection(post, myErrorHandler).get();
      LOGGER.debug(() -> "Multipart upload " + uploadId + " signaling " + (isSuccessful ? "success" : "failure") + " finished");
    } catch (Exception e) {
      LOGGER.warnAndDebugDetails("Multipart upload " + uploadId + " signaling " + (isSuccessful ? "success" : "failure") + " failed: " + e.getMessage(), e);
      throw e;
    }
  }

  private List<Pair<String, String>> getParameters(@NotNull String objectKey, @Nullable List<String> etags, @NotNull String uploadId, boolean isSuccessful) {
    final List<Pair<String, String>> parameters = new ArrayList<>();
    parameters.add(new Pair<>(OBJECT_KEY, objectKey));
    parameters.add(new Pair<>(OBJECT_KEY + "_BASE64", Base64.getEncoder().encodeToString(objectKey.getBytes(StandardCharsets.UTF_8))));
    parameters.add(new Pair<>(FINISH_UPLOAD, uploadId));
    parameters.add(new Pair<>(UPLOAD_SUCCESSFUL, String.valueOf(isSuccessful)));

    if (isSuccessful && etags != null) {
      etags.forEach(etag -> parameters.add(new Pair<>(ETAGS, etag)));
    }

    return parameters;
  }


  @NotNull
  private EntityProducer multipartRequestEntity(@NotNull final String s3ObjectKey, @NotNull final List<String> digests, @Nullable String uploadId, @Nullable Long ttl) {
    try {
      return requestEntity(PresignedUrlRequestSerializer.serializeRequestV2(PresignedUrlListRequestDto.forObjectKeyMultipart(s3ObjectKey, uploadId, digests, ttl)));
    } catch (UnsupportedEncodingException e) {
      LOGGER.warnAndDebugDetails("Unsupported encoding", e);
      throw new MisconfigurationException(e);
    }
  }

  @NotNull
  private EntityProducer requestEntity(@NotNull String xml) throws UnsupportedEncodingException {
    return new EntityProducer() {
      @Override
      public HttpEntity entity4() {
        return new StringEntity(xml, ContentType.create(ContentType.APPLICATION_XML.getMimeType(), StandardCharsets.UTF_8));
      }

      @Override
      public RequestEntity entity3() {
        try {
          return new StringRequestEntity(xml, ContentType.APPLICATION_XML.getMimeType(), StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
          throw new RuntimeException(e);
        }
      }
    };
  }

  @NotNull
  private EntityProducer s3ObjectKeysRequestEntity(@NotNull Collection<String> s3ObjectKeys, Map<String, String> digests) {
    try {
      final List<Pair<String, String>> keysWithDigests = s3ObjectKeys.stream().map(k -> Pair.create(k, digests.get(k))).collect(Collectors.toList());
      return requestEntity(PresignedUrlRequestSerializer.serializeRequestV2(PresignedUrlListRequestDto.forObjectKeysWithDigests(keysWithDigests)));
    } catch (UnsupportedEncodingException e) {
      LOGGER.warnAndDebugDetails("Unsupported encoding", e);
      throw new MisconfigurationException(e);
    }
  }

  @NotNull
  private EntityProducer s3ObjectKeyRequestEntity(@NotNull String objectKey, @Nullable String digest, @Nullable Long ttl) {
    try {
      return requestEntity(PresignedUrlRequestSerializer.serializeRequestV2(PresignedUrlListRequestDto.forObjectKeyWithDigest(objectKey, digest, ttl)));
    } catch (UnsupportedEncodingException e) {
      LOGGER.warnAndDebugDetails("Unsupported encoding", e);
      throw new MisconfigurationException(e);
    }
  }


  @NotNull
  private HTTPRequestBuilder postTemplate() throws URISyntaxException {
    final HTTPRequestBuilder requestBuilder = new HTTPRequestBuilder(myPresignedUrlsPostUrl);
    requestBuilder
      .withMethod(POST)
      .withHeader(HttpHeaders.USER_AGENT, HttpUserAgent.getUserAgent())
      .withHeader("Accept", "application/xml")
      .withHeader("Accept-Charset", StandardCharsets.UTF_8.name())
      .withTimeout(myTeamCityConnectionConfiguration.getConnectionTimeout() * 1000)
      .withAuthenticateHeader(myTeamCityConnectionConfiguration.getCredentials())
      .allowNonSecureConnection(true);


    addAdditionalHeaders(requestBuilder);
    return requestBuilder;
  }

  private void validateClient() {
    if (myShutdown.get()) {
      LOGGER.warn("TeamCity presigned urls provider client already shut down");
      throw new ClientAlreadyShutdownException("TeamCity presigned urls provider client already shut down");
    }
  }

  private void addAdditionalHeaders(HTTPRequestBuilder request) {
    HashMap<String, String> headerToProviderMap = new HashMap<>();
    ArtifactTransportAdditionalHeadersProvider.Configuration configuration = () -> POST.getName();
    for (ArtifactTransportAdditionalHeadersProvider extension : myAdditionalHeadersProviders) {
      List<ArtifactTransportAdditionalHeadersProvider.Header> headers = extension.getHeaders(configuration);
      String extensionName = extension.getClass().getName();
      for (ArtifactTransportAdditionalHeadersProvider.Header header : headers) {
        String existingExtensionsName = headerToProviderMap.get(header.getName().toUpperCase());
        if (existingExtensionsName == null) {
          request.withHeader(header.getName(), header.getValue());
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
