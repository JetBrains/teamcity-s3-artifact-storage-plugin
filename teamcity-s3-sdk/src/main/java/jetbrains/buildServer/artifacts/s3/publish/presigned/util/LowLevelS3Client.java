package jetbrains.buildServer.artifacts.s3.publish.presigned.util;

import java.io.File;
import java.io.IOException;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.http.HttpUserAgent;
import jetbrains.buildServer.http.HttpUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.amazon.S3Util.S3AdvancedConfiguration;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.methods.*;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpResponseException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LowLevelS3Client implements AutoCloseable {
  @NotNull
  private final HttpClient myHttpClient;

  public LowLevelS3Client(@NotNull final S3AdvancedConfiguration s3Config) {
    myHttpClient = HttpUtil.createHttpClient(s3Config.getConnectionTimeout());
    myHttpClient.setHttpConnectionManager(HttpClientUtil.createConnectionManager(s3Config.getConnectionTimeout(), s3Config.getNThreads()));
  }

  public void uploadFile(@NotNull final String url, @NotNull final File file) throws IOException {
    putEntity(url, new FileRequestEntity(file, S3Util.getContentType(file)));
  }

  @Nullable
  public String fetchETag(@NotNull final String url) throws IOException {
    return parseEtags(head(url));
  }

  @NotNull
  public String uploadFilePart(@NotNull final String url, @NotNull final File file, final long start, final long size) throws IOException {
    final HttpMethodBase request = putEntity(url, new RepeatableFilePartRequestEntity(file, start, size));
    return parseEtags(request);
  }

  @NotNull
  private String parseEtags(@NotNull final HttpMethodBase request) throws HttpResponseException {
    final Header eTags = request.getResponseHeader("ETag");
    if (eTags != null) {
      return StringUtil.unquoteString(eTags.getValue());
    } else {
      throw new HttpResponseException(HttpStatus.SC_BAD_REQUEST, "Unexpected response from S3: doesn't contain etags headers");
    }
  }

  @NotNull
  private HttpMethodBase head(@NotNull final String url) throws IOException {
    final HttpMethodBase request = headRequest(url);
    HttpClientUtil.executeAndReleaseConnection(myHttpClient, request);
    return request;
  }

  @NotNull
  private EntityEnclosingMethod putEntity(@NotNull final String url, @NotNull final RequestEntity entity) throws IOException {
    EntityEnclosingMethod request = putRequest(url, entity);
    HttpClientUtil.executeAndReleaseConnection(myHttpClient, request);
    return request;
  }

  @NotNull
  private EntityEnclosingMethod putRequest(@NotNull final String url, @NotNull final RequestEntity requestEntity) {
    final EntityEnclosingMethod request = putRequest(url);
    request.setRequestEntity(requestEntity);
    return request;
  }

  @NotNull
  private EntityEnclosingMethod putRequest(@NotNull String url) {
    return setHeader(new PutMethod(url));
  }

  @NotNull
  private HeadMethod headRequest(@NotNull final String url) {
    return setHeader(new HeadMethod(url));
  }

  @NotNull
  private <T extends HttpMethodBase> T setHeader(@NotNull final T request) {
    request.setRequestHeader(HttpHeaders.USER_AGENT, HttpUserAgent.getUserAgent());
    return request;
  }

  @Override
  public void close() {
    HttpClientUtil.shutdown(myHttpClient);
  }
}
