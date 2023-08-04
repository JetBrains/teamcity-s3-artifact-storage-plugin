package jetbrains.buildServer.artifacts.s3.publish.presigned.util;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import jetbrains.buildServer.artifacts.s3.S3Configuration;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.artifacts.s3.exceptions.FileUploadFailedException;
import jetbrains.buildServer.artifacts.s3.publish.errors.*;
import jetbrains.buildServer.http.HttpUserAgent;
import jetbrains.buildServer.util.HTTPRequestBuilder;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.ThreadUtil;
import jetbrains.buildServer.util.executors.ExecutorsFactory;
import jetbrains.buildServer.util.http.EntityProducer;
import jetbrains.buildServer.util.http.HttpMethod;
import org.apache.commons.httpclient.methods.FileRequestEntity;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.FileEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LowLevelS3Client implements AutoCloseable {
  @NotNull
  private final HttpResponseErrorHandler myErrorHandler =
    new CompositeHttpRequestErrorHandler(new S3DirectResponseErrorHandler(), new S3ServerResponseErrorHandler(), new TeamCityPresignedUrlsProviderErrorHandler());
  private final boolean myCheckConsistency;
  @NotNull
  private final Map<String, String> myAdditionalHeaders;
  private final ExecutorService myExecutorService;
  private final int myConnectionTimeout;
  private final boolean myAllowPlainHttpUpload;

  public LowLevelS3Client(@NotNull final S3Configuration s3Config) {
    myExecutorService = ExecutorsFactory.newFixedDaemonExecutor(S3Constants.S3_STORAGE_TYPE, s3Config.getNThreadsForFileParts());
    myConnectionTimeout = s3Config.getAdvancedConfiguration().getConnectionTimeout() * 1000;
    myCheckConsistency = s3Config.getAdvancedConfiguration().isConsistencyCheckEnabled();
    myAdditionalHeaders = new HashMap<>();
    myAdditionalHeaders.put("x-amz-acl", s3Config.getAcl().toString());
    myAllowPlainHttpUpload = s3Config.getAdvancedConfiguration().isAllowPlainHttpUpload();
  }

  @NotNull
  public CompletableFuture<String> uploadFile(@NotNull final String url, @NotNull final File file, @Nullable String digest) throws URISyntaxException {
    return put(url, new EntityProducer() {
      @Override
      public HttpEntity entity4() {
        return new FileEntity(file, ContentType.parse(S3Util.getContentType(file)));
      }

      @Override
      public RequestEntity entity3() {
        return new FileRequestEntity(file, S3Util.getContentType(file));
      }
    }, digest, myAdditionalHeaders);
  }

  @NotNull
  public CompletableFuture<String> uploadFilePart(@NotNull final String url, @NotNull final FilePart filePart) throws URISyntaxException {
    return put(url, new EntityProducer() {
      @Override
      public HttpEntity entity4() {
        return new RepeatableFilePartRequestEntityApache43(filePart, S3Util.getContentType(filePart.getFile()));
      }

      @Override
      public RequestEntity entity3() {
        return new RepeatableFilePartRequestEntityApacheLegacy(filePart, S3Util.getContentType(filePart.getFile()));
      }
    }, filePart.getDigest(), Collections.emptyMap());
  }

  @NotNull
  private String parseEtags(@NotNull final HttpResponseAdapter response) {
    final String eTags = response.getHeader("ETag");
    if (!StringUtil.isEmpty(eTags)) {
      return StringUtil.unquoteString(eTags);
    } else {
      throw new FileUploadFailedException("Response does not contain etags", true);
    }
  }

  @NotNull
  private CompletableFuture<String> put(@NotNull final String url, @NotNull final EntityProducer requestEntity, @Nullable String digest, @NotNull final Map<String, String> headers)
    throws URISyntaxException {
    final HTTPRequestBuilder requestBuilder = new HTTPRequestBuilder(url);
    requestBuilder
      .withMethod(HttpMethod.PUT)
      .withHeader(HttpHeaders.USER_AGENT, HttpUserAgent.getUserAgent())
      .withHeader("Accept", "application/xml")
      .withData(requestEntity)
      .withTimeout(myConnectionTimeout)
      .allowNonSecureConnection(myAllowPlainHttpUpload);

    headers.forEach(requestBuilder::withHeader);
    if (myCheckConsistency && digest != null) {
      requestBuilder.withHeader("Content-MD5", digest);
    }

    return HttpClientUtil
      .executeAndReleaseConnection(requestBuilder, myErrorHandler, myExecutorService)
      .thenApply(this::parseEtags);
  }

  @Override
  public void close() {
    ThreadUtil.shutdownGracefully(myExecutorService, S3Constants.S3_STORAGE_TYPE);
  }
}
