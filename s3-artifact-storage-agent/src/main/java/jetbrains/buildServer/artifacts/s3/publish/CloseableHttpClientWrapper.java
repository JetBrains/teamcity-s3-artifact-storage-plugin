package jetbrains.buildServer.artifacts.s3.publish;

import org.apache.commons.httpclient.HttpClient;
import org.jetbrains.annotations.NotNull;

public class CloseableHttpClientWrapper implements AutoCloseable {
  @NotNull
  private final HttpClient myHttpClient;

  public CloseableHttpClientWrapper(@NotNull final HttpClient httpClient) {
    myHttpClient = httpClient;
  }

  @Override
  public void close() {
    HttpClientUtil.shutdown(myHttpClient);
  }

  @NotNull
  public HttpClient getHttpClient() {
    return myHttpClient;
  }
}
