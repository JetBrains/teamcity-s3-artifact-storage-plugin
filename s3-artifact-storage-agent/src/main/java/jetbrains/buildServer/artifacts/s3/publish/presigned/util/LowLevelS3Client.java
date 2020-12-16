package jetbrains.buildServer.artifacts.s3.publish.presigned.util;

import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.IOException;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.http.HttpUserAgent;
import jetbrains.buildServer.util.StringUtil;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.FileRequestEntity;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpResponseException;
import org.jetbrains.annotations.NotNull;

public class LowLevelS3Client implements AutoCloseable {
  @NotNull
  private static final Logger LOGGER = Logger.getInstance(LowLevelS3Client.class);
  @NotNull
  private final HttpClient myHttpClient;

  public LowLevelS3Client(@NotNull final HttpClient httpClient) {
    myHttpClient = httpClient;
  }

  @Override
  public void close() {
    HttpClientUtil.shutdown(myHttpClient);
  }

  public void uploadFile(@NotNull final String url, @NotNull final File file) throws IOException {
    final PutMethod putMethod = new PutMethod(url);
    putMethod.setRequestHeader(HttpHeaders.USER_AGENT, HttpUserAgent.getUserAgent());
    putMethod.setRequestEntity(new FileRequestEntity(file, S3Util.getContentType(file)));
    HttpClientUtil.executeAndReleaseConnection(myHttpClient, putMethod);
  }

  @NotNull
  public String uploadFilePart(@NotNull final String url, @NotNull final byte[] content, @NotNull final String contentType) throws IOException {
    final PutMethod request = new PutMethod(url);
    request.setRequestHeader(HttpHeaders.USER_AGENT, HttpUserAgent.getUserAgent());
    request.setRequestEntity(new ByteArrayRequestEntity(content, contentType));
    HttpClientUtil.executeAndReleaseConnection(myHttpClient, request);
    final Header eTags = request.getResponseHeader("ETag");
    if (eTags != null) {
      return StringUtil.unquoteString(eTags.getValue());
    } else {
      throw new HttpResponseException(HttpStatus.SC_BAD_REQUEST, "Unexpected response from S3: doesn't contain etags headers");
    }
  }
}
