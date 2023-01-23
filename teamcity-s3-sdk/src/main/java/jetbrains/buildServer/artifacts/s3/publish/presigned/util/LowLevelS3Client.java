package jetbrains.buildServer.artifacts.s3.publish.presigned.util;

import java.io.File;
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
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.ThreadUtil;
import jetbrains.buildServer.util.executors.ExecutorsFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LowLevelS3Client implements AutoCloseable {
  @NotNull
  private final CloseableHttpClient myHttpClient;
  @NotNull
  private final HttpResponseErrorHandler myErrorHandler =
    new CompositeHttpRequestErrorHandler(new S3DirectResponseErrorHandler(), new S3ServerResponseErrorHandler(), new TeamCityPresignedUrlsProviderErrorHandler());
  private final boolean myCheckConsistency;
  @NotNull
  private final Map<String, String> myAdditionalHeaders;
  private final ExecutorService myExecutorService;

  public LowLevelS3Client(@NotNull final S3Configuration s3Config) {
    myExecutorService = ExecutorsFactory.newFixedDaemonExecutor(S3Constants.S3_STORAGE_TYPE, s3Config.getAdvancedConfiguration().getNThreads());
    int connectionTimeout = s3Config.getAdvancedConfiguration().getConnectionTimeout() * 1000;
    myHttpClient = HttpClients.custom()
                              .setDefaultRequestConfig(RequestConfig.custom()
                                                                         .setConnectTimeout(connectionTimeout)
                                                                         .setSocketTimeout(connectionTimeout)
                                                                         .build())
                              .build();

    myCheckConsistency = s3Config.getAdvancedConfiguration().isConsistencyCheckEnabled();
    myAdditionalHeaders = new HashMap<>();
    myAdditionalHeaders.put("x-amz-acl", s3Config.getAcl().toString());
  }

  @NotNull
  public CompletableFuture<String> uploadFile(@NotNull final String url, @NotNull final File file, @Nullable String digest) {
    return put(url, new FileEntity(file, ContentType.parse(S3Util.getContentType(file))), digest, myAdditionalHeaders);
  }

  @NotNull
  public CompletableFuture<String> uploadFilePart(@NotNull final String url, @NotNull final FilePart filePart) {
    return put(url, new RepeatableFilePartRequestEntity(filePart, S3Util.getContentType(filePart.getFile())), filePart.getDigest(), Collections.emptyMap());
  }

  @NotNull
  private String parseEtags(@NotNull final HttpResponse response) {
    final org.apache.http.Header eTags = response.getFirstHeader("ETag");
    if (eTags != null) {
      return StringUtil.unquoteString(eTags.getValue());
    } else {
      throw new FileUploadFailedException("Response does not contain etags", true);
    }
  }

  @NotNull
  private CompletableFuture<String> put(@NotNull final String url, @NotNull final HttpEntity requestEntity, @Nullable String digest, @NotNull final Map<String, String> headers) {
    final HttpPut request = new HttpPut(url);
    request.setHeader(HttpHeaders.USER_AGENT, HttpUserAgent.getUserAgent());
    request.setEntity(requestEntity);
    headers.forEach((name, value) -> request.setHeader(name, value));
    request.setHeader("Accept", "application/xml");
    if (myCheckConsistency && digest != null) {
      request.setHeader("Content-MD5", digest);
    }
    return HttpClientUtil
      .executeAndReleaseConnection(myHttpClient, request, myErrorHandler, myExecutorService)
      .thenApply(response -> {
        return parseEtags(response);
      });
  }

  @Override
  public void close() {
    ThreadUtil.shutdownGracefully(myExecutorService, S3Constants.S3_STORAGE_TYPE);
    HttpClientUtil.shutdown(myHttpClient);
  }
}
