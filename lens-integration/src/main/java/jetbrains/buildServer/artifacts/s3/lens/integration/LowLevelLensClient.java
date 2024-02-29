package jetbrains.buildServer.artifacts.s3.lens.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import jetbrains.buildServer.artifacts.s3.lens.integration.dto.UploadFileEvent;
import jetbrains.buildServer.artifacts.s3.lens.integration.dto.UploadInfoEvent;
import jetbrains.buildServer.artifacts.s3.publish.errors.HttpResponseAdapter;
import jetbrains.buildServer.artifacts.s3.publish.presigned.upload.TeamCityConnectionConfiguration;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.HttpClientUtil;
import jetbrains.buildServer.http.HttpUserAgent;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.util.HTTPRequestBuilder;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.http.HttpMethod;
import org.apache.http.HttpHeaders;
import org.jetbrains.annotations.NotNull;

public class LowLevelLensClient {
  private static final String S3_UPLOAD_OBJECT_EVENT = "artifact_object_upload";
  private static final String S3_UPLOAD_INFO_EVENT = "artifacts_upload";
  private static final String LENS_CUSTOM_EVENTS_ENDPOINT = "/lens/customBuildEvent?buildId=%d&eventName=%s";
  private final String myLensEndpointUrl;
  private final String myLensProbingEndpointUrl;
  private final TeamCityConnectionConfiguration myTeamCityConnectionConfiguration;

  public LowLevelLensClient(TeamCityConnectionConfiguration teamCityConnectionConfiguration) {
    myLensEndpointUrl = teamCityConnectionConfiguration.getTeamCityUrl() + "/app/" + StringUtil.removeLeadingSlash(LENS_CUSTOM_EVENTS_ENDPOINT);
    myLensProbingEndpointUrl = teamCityConnectionConfiguration.getTeamCityUrl() + "/app/lens/customBuildEvent";
    myTeamCityConnectionConfiguration = teamCityConnectionConfiguration;
  }

  public CompletableFuture<HttpResponseAdapter> publishUploadFileEvent(final long buildId, @NotNull final UploadFileEvent event)
    throws URISyntaxException, JsonProcessingException {
    return publishEvent(buildId, event, S3_UPLOAD_OBJECT_EVENT);
  }

  public CompletableFuture<HttpResponseAdapter> publishUploadInfoEvent(final long buildId, @NotNull final UploadInfoEvent event)
    throws URISyntaxException, JsonProcessingException {
    return publishEvent(buildId, event, S3_UPLOAD_INFO_EVENT);
  }

  @NotNull
  private CompletableFuture<HttpResponseAdapter> publishEvent(long buildId, @NotNull Object event, @NotNull final String eventName)
    throws URISyntaxException, JsonProcessingException {

    // construct the request towards the TeamCity server
    HTTPRequestBuilder requestBuilder = new HTTPRequestBuilder(String.format(myLensEndpointUrl, buildId, eventName));
    requestBuilder
      .withMethod(HttpMethod.POST)
      .withHeader(HttpHeaders.USER_AGENT, HttpUserAgent.getUserAgent())
      .withTimeout(myTeamCityConnectionConfiguration.getConnectionTimeout() * 1000)
      .withAuthenticateHeader(myTeamCityConnectionConfiguration.getCredentials())
      .allowNonSecureConnection(true) // TeamCity server can be configured to use plain HTTP
      .withData(new JsonEntityProducer(event));

    // execute the request
    return HttpClientUtil
      .executeAndReleaseConnection(requestBuilder, new LensResponseErrorHandler());
  }

  public boolean probeLensConnection() {
    try {
      HTTPRequestBuilder requestBuilder = new HTTPRequestBuilder(myLensProbingEndpointUrl);
      requestBuilder
        .withMethod("OPTIONS")
        .withHeader(HttpHeaders.USER_AGENT, HttpUserAgent.getUserAgent())
        .withTimeout(myTeamCityConnectionConfiguration.getConnectionTimeout() * 1000)
        .withAuthenticateHeader(myTeamCityConnectionConfiguration.getCredentials())
        .allowNonSecureConnection(true); // TeamCity server can be configured to use plain HTTP

      // execute the request
      return HttpClientUtil
        .executeAndReleaseConnection(requestBuilder, new LensResponseErrorHandler())
        .thenApply(response -> {
          String acceptHeader = response.getHeader("Allow");
          return response.getStatusCode() == 200 && acceptHeader != null && acceptHeader.contains(HttpMethod.POST.getName());
        }).get();
    } catch (Exception e) {
      Loggers.AGENT.warnAndDebugDetails(
        String.format("Failed to probe lens connection: url=%s", myLensProbingEndpointUrl), e);
      return false;
    }
  }

}
