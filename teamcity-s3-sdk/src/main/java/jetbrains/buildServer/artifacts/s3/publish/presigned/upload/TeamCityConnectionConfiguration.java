package jetbrains.buildServer.artifacts.s3.publish.presigned.upload;

import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.xmlrpc.NodeIdHolder;
import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.jetbrains.annotations.NotNull;

public class TeamCityConnectionConfiguration {
  @NotNull
  private static final String MAX_TOTAL_CONNECTIONS_PARAM = "teamcity.s3.artifactUploader.maxTotalConnections";
  private final int myNThreads = TeamCityProperties.getInteger(MAX_TOTAL_CONNECTIONS_PARAM, MultiThreadedHttpConnectionManager.DEFAULT_MAX_TOTAL_CONNECTIONS);
  @NotNull
  private final String myTeamCityUrl;
  @NotNull
  private final String myAccessUser;
  @NotNull
  private final String myAccessCode;
  @NotNull
  private final String myUrlsProviderPath;
  private final int myConnectionTimeout;

  private final NodeIdHolder myNodeIdHolder;
  private final int myRetriesNum;
  private final int myRetryDelay;

  public TeamCityConnectionConfiguration(@NotNull final String teamCityUrl,
                                         @NotNull final String urlsProviderPath,
                                         @NotNull final String accessUser,
                                         @NotNull final String accessCode,
                                         @NotNull final NodeIdHolder nodeIdHolder,
                                         final int connectionTimeout,
                                         int retriesNum,
                                         int retryDelay) {
    myTeamCityUrl = teamCityUrl;
    myAccessUser = accessUser;
    myAccessCode = accessCode;
    myConnectionTimeout = connectionTimeout;
    myUrlsProviderPath = urlsProviderPath;
    myNodeIdHolder = nodeIdHolder;
    myRetriesNum = retriesNum;
    myRetryDelay = retryDelay;
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

  @NotNull
  public Credentials getCredentials() {
    return new UsernamePasswordCredentials(myAccessUser, myAccessCode);
  }

  @NotNull
  public String getUrlsProviderPath() {
    return myUrlsProviderPath;
  }

  @NotNull
  public NodeIdHolder getNodeIdHolder() {
    return myNodeIdHolder;
  }

  public int getRetriesNum() {
    return myRetriesNum;
  }

  public int getRetryDelay() {
    return myRetryDelay;
  }
}
