package jetbrains.buildServer.artifacts.s3.download;

import java.io.IOException;
import jetbrains.buildServer.artifacts.impl.DependencyHttpHelper;
import jetbrains.buildServer.http.HttpUserAgent;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpState;
import org.jetbrains.annotations.NotNull;

public final class S3HttpClient {
  @NotNull
  private final HttpClient myHttpClient;
  @NotNull
  private final DependencyHttpHelper myDependencyHttpHelper;
  @NotNull
  private final String myServerUrl;

  public S3HttpClient(@NotNull HttpClient httpClient, @NotNull DependencyHttpHelper dependencyHttpHelper, @NotNull String serverUrl) {
    myHttpClient = httpClient;
    myDependencyHttpHelper = dependencyHttpHelper;
    myServerUrl = serverUrl;
  }

  public int execute(@NotNull HttpMethod request) throws IOException {
    HttpUserAgent.addHeader(request).setFollowRedirects(false);
    myDependencyHttpHelper.addAdditionalHeaders(request);

    // we need to clear the state because the HttpClient is configured to provide credentials for TC server in the Authorization header
    // we don't want to provide them to untrusted parties (see jetbrains.buildServer.artifacts.impl.HttpTransport)
    return !request.getURI().getURI().startsWith(myServerUrl)
           ? myHttpClient.executeMethod(null, request, new HttpState())
           : myHttpClient.executeMethod(request);
  }
}
