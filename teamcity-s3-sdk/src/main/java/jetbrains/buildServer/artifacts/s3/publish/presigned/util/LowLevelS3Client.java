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
import org.apache.commons.httpclient.methods.EntityEnclosingMethod;
import org.apache.commons.httpclient.methods.FileRequestEntity;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpResponseException;
import org.jetbrains.annotations.NotNull;

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

  @NotNull
  public String uploadFilePart(@NotNull final String url, @NotNull final File file, final long start, final long size) throws IOException {
    final EntityEnclosingMethod request = putEntity(url, new RepeatableFilePartRequestEntity(file, start, size));

    final Header eTags = request.getResponseHeader("ETag");
    if (eTags != null) {
      return StringUtil.unquoteString(eTags.getValue());
    } else {
      throw new HttpResponseException(HttpStatus.SC_BAD_REQUEST, "Unexpected response from S3: doesn't contain etags headers");
    }
  }

  @NotNull
  private EntityEnclosingMethod putEntity(@NotNull final String url, @NotNull RequestEntity entity) throws IOException {
    EntityEnclosingMethod request = createRequest(url, entity);
    HttpClientUtil.executeAndReleaseConnection(myHttpClient, request);
    return request;
  }

  @NotNull
  private EntityEnclosingMethod createRequest(@NotNull final String url, @NotNull final RequestEntity requestEntity) {
    final EntityEnclosingMethod request = requestTemplate(url);
    request.setRequestEntity(requestEntity);
    return request;
  }

  @NotNull
  private EntityEnclosingMethod requestTemplate(@NotNull String url) {
    final PutMethod request = new PutMethod(url);
    request.setRequestHeader(HttpHeaders.USER_AGENT, HttpUserAgent.getUserAgent());
    return request;
  }

  @Override
  public void close() {
    HttpClientUtil.shutdown(myHttpClient);
  }
}
