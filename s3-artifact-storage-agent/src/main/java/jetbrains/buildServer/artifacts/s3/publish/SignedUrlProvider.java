package jetbrains.buildServer.artifacts.s3.publish;

import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.artifacts.s3.S3PreSignUrlHelper;
import jetbrains.buildServer.http.HttpUserAgent;
import jetbrains.buildServer.http.HttpUtil;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.UptodateValue;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.artifacts.s3.S3Constants.ARTEFACTS_S3_UPLOAD_PRESIGN_URLS_HTML;

public class SignedUrlProvider implements AutoCloseable {
  @NotNull
  private static final Logger LOGGER = Logger.getInstance(SignedUrlProvider.class);
  @NotNull
  private static final String MAX_TOTAL_CONNECTIONS_PARAM = "teamcity.s3.artifactUploader.maxTotalConnections";
  @NotNull
  private static final String SIGNED_URL_CACHE_TTL = "teamcity.s3.artifactUploader.signedUrlCacheTtl";
  @NotNull
  private final HttpClient myTeamCityClient;
  @NotNull
  private final String myTargetUrl;
  @NotNull
  private final List<String> myS3ObjectKeys;
  @NotNull
  private final UptodateValue<Map<String, URL>> myCache;

  public SignedUrlProvider(@NotNull final TeamCityConnectionConfiguration tcConfig, @NotNull final Collection<String> s3ObjectKeys) {
    myTargetUrl = tcConfig.getTeamCityUrl() + "/httpAuth" + ARTEFACTS_S3_UPLOAD_PRESIGN_URLS_HTML;
    myTeamCityClient = createClient(tcConfig);
    myS3ObjectKeys = new ArrayList<>(s3ObjectKeys);
    myCache = new UptodateValue<>(this::fetchUploadUrlsFromServer, TeamCityProperties.getInteger(SIGNED_URL_CACHE_TTL, 60000));
  }


  @NotNull
  public URL getUrl(@NotNull final String s3ObjectKey) {
    return myCache.getValue().get(s3ObjectKey);
  }


  @NotNull
  private Map<String, URL> fetchUploadUrlsFromServer() {
    try {
      final PostMethod post = postTemplate();
      post.setRequestEntity(requestEntity(myS3ObjectKeys));
      final String responseBody = HttpClientUtil.executeReleasingConnectionAndReadResponseBody(myTeamCityClient, post);
      return S3PreSignUrlHelper.readPreSignUrlMapping(responseBody);
    } catch (HttpClientUtil.HttpErrorCodeException | IOException e) {
      LOGGER.warnAndDebugDetails("Failed resolving S3 pre-signed URL, got exception " + e.getMessage(), e);
      throw new FetchFailedException(e);
    }
  }


  @NotNull
  private HttpClient createClient(@NotNull final TeamCityConnectionConfiguration config) {
    try {
      final HttpClient httpClient = HttpUtil.createHttpClient(config.getConnectionTimeout(), new URL(myTargetUrl), config.getCredentials());
      final HttpConnectionManager httpConnectionManager = HttpClientUtil.createConnectionManager(config.getConnectionTimeout(), config.getNThreads());
      httpClient.setHttpConnectionManager(httpConnectionManager);
      return httpClient;
    } catch (MalformedURLException e) {
      LOGGER.warnAndDebugDetails("Malformed url to TeamCity server", e);
      throw new MisconfigurationException(e);
    }
  }

  @NotNull
  private PostMethod postTemplate() {
    final PostMethod post = new PostMethod(myTargetUrl);
    post.addRequestHeader(HttpHeaders.USER_AGENT, HttpUserAgent.getUserAgent());
    post.setDoAuthentication(true);
    return post;
  }

  @NotNull
  private StringRequestEntity requestEntity(@NotNull Collection<String> s3ObjectKeys) {
    try {
      return new StringRequestEntity(S3PreSignUrlHelper.writeS3ObjectKeys(s3ObjectKeys), ContentType.APPLICATION_XML.getMimeType(), StandardCharsets.UTF_8.name());
    } catch (UnsupportedEncodingException e) {
      LOGGER.warnAndDebugDetails("Unsupported encoding", e);
      throw new MisconfigurationException(e);
    }
  }

  @Override
  public void close() {
    HttpClientUtil.shutdown(myTeamCityClient);
  }

  static class TeamCityConnectionConfiguration {
    @NotNull
    private final String myTeamCityUrl;
    @NotNull
    private final String myAccessUser;
    @NotNull
    private final String myAccessCode;
    private final int myConnectionTimeout;
    private final int myNThreads = TeamCityProperties.getInteger(MAX_TOTAL_CONNECTIONS_PARAM, MultiThreadedHttpConnectionManager.DEFAULT_MAX_TOTAL_CONNECTIONS);

    public TeamCityConnectionConfiguration(@NotNull final String teamCityUrl, @NotNull final String accessUser, @NotNull final String accessCode, final int connectionTimeout) {
      myTeamCityUrl = teamCityUrl;
      myAccessUser = accessUser;
      myAccessCode = accessCode;
      myConnectionTimeout = connectionTimeout;
    }

    public int getConnectionTimeout() {
      return myConnectionTimeout;
    }

    public int getNThreads() {
      return myNThreads;
    }

    @NotNull
    public String getTeamCityUrl() {
      return myTeamCityUrl;
    }

    public Credentials getCredentials() {
      return new UsernamePasswordCredentials(myAccessUser, myAccessCode);
    }
  }

  static class MisconfigurationException extends RuntimeException {
    public MisconfigurationException(@NotNull final Throwable cause) {
      super(cause);
    }
  }

  static class FetchFailedException extends RuntimeException {
    public FetchFailedException(@NotNull final Throwable cause) {
      super(cause);
    }
  }
}
