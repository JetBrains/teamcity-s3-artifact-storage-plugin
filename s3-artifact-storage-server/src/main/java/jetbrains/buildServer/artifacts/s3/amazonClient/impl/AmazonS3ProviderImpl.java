package jetbrains.buildServer.artifacts.s3.amazonClient.impl;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.services.cloudfront.AmazonCloudFront;
import com.amazonaws.services.cloudfront.AmazonCloudFrontClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.intellij.openapi.util.Pair;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import jetbrains.buildServer.artifacts.ArtifactStorageSettings;
import jetbrains.buildServer.artifacts.s3.CachingSocketFactory;
import jetbrains.buildServer.artifacts.s3.amazonClient.AmazonS3Provider;
import jetbrains.buildServer.artifacts.s3.amazonClient.WithCloudFrontClient;
import jetbrains.buildServer.artifacts.s3.amazonClient.WithS3Client;
import jetbrains.buildServer.clouds.amazon.connector.impl.AwsConnectionCredentials;
import jetbrains.buildServer.clouds.amazon.connector.utils.clients.ClientConfigurationBuilder;
import jetbrains.buildServer.clouds.amazon.connector.utils.parameters.AwsCloudConnectorConstants;
import jetbrains.buildServer.clouds.amazon.connector.utils.parameters.ParamUtil;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.IOGuard;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.connections.credentials.ConnectionCredentials;
import jetbrains.buildServer.serverSide.connections.credentials.ConnectionCredentialsException;
import jetbrains.buildServer.serverSide.connections.credentials.ProjectConnectionCredentialsManager;
import jetbrains.buildServer.util.ExceptionUtil;
import jetbrains.buildServer.util.SystemTimeService;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import jetbrains.buildServer.util.amazon.S3Util;
import org.apache.commons.lang.StringUtils;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.artifacts.s3.BucketLocationFetcher.getRegionName;
import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_ENABLE_ACCELERATE_MODE;
import static jetbrains.buildServer.artifacts.s3.S3Util.*;
import static jetbrains.buildServer.clouds.amazon.connector.utils.parameters.AwsCommonParameters.SSL_CERT_DIRECTORY_PARAM;

public class AmazonS3ProviderImpl implements AmazonS3Provider {
  private final String S3_SIGNER_TYPE = "AWSS3V4SignerType";

  private final ProjectManager myProjectManager;
  private final ProjectConnectionCredentialsManager myProjectConnectionCredentialsManager;
  private final Cache<Pair<String, String>, Map<String, String>> myCorrectedSettings = CacheBuilder.newBuilder()
                                                                                                   .expireAfterAccess(24, java.util.concurrent.TimeUnit.HOURS)
                                                                                                   .build();

  public AmazonS3ProviderImpl(@NotNull final ProjectManager projectManager,
                              @NotNull final ProjectConnectionCredentialsManager projectConnectionCredentialsManager) {
    myProjectManager = projectManager;
    myProjectConnectionCredentialsManager = projectConnectionCredentialsManager;
  }

  @Nullable
  private static String extractCorrectedRegion(@NotNull final Throwable e) {
    @Nullable final AmazonS3Exception awsException = e instanceof AmazonS3Exception ? (AmazonS3Exception)e : ExceptionUtil.getCause(e, AmazonS3Exception.class);
    if (awsException != null && TeamCityProperties.getBooleanOrTrue("teamcity.internal.storage.s3.autoCorrectRegion") && awsException.getAdditionalDetails() != null) {
      final String correctRegion = awsException.getAdditionalDetails().get("Region");
      if (correctRegion != null) {
        return correctRegion;
      } else {
        return awsException.getAdditionalDetails().get("x-amz-bucket-region");
      }
    } else {
      return null;
    }
  }

  @NotNull
  @Override
  public AmazonS3 fromS3Settings(@NotNull final Map<String, String> s3Settings,
                                 @NotNull final String projectId) throws ConnectionCredentialsException {
    return fromS3Configuration(s3Settings, projectId, null);
  }

  public <T, E extends Exception> T withS3ClientShuttingDownImmediately(@NotNull final Map<String, String> params,
                                                                        @NotNull final String projectId,
                                                                        @NotNull final WithS3Client<T, E> withClient) throws ConnectionCredentialsException {
    return withCorrectingRegionAndAcceleration(params, projectId, withClient, true);
  }

  public <T, E extends Exception> T withS3Client(@NotNull final Map<String, String> params,
                                                 @NotNull final String projectId,
                                                 @NotNull final WithS3Client<T, E> withClient) throws ConnectionCredentialsException {
    return withCorrectingRegionAndAcceleration(params, projectId, withClient, false);
  }

  @Override
  public void shutdownClient(@NotNull final AmazonS3 s3Client) {
    try {
      s3Client.shutdown();
    } catch (Exception e) {
      Loggers.CLOUD.warnAndDebugDetails("Shutting down s3 client " + s3Client + " failed.", e);
    }
  }

  @Override
  public <T, E extends Exception> T withCorrectingRegionAndAcceleration(@NotNull final Map<String, String> settings,
                                                                        @NotNull final String projectId,
                                                                        @NotNull final WithS3Client<T, E> action,
                                                                        final boolean shutdownImmediately) throws ConnectionCredentialsException {
    try {
      return withS3Client(settings, projectId, action, shutdownImmediately);
    } catch (AmazonS3Exception | ConnectionCredentialsException s3Exception) {
      final Map<String, String> correctedSettings = extractCorrectedSettings(projectId, settings, s3Exception);
      return withS3Client(correctedSettings, projectId, action, shutdownImmediately);
    }
  }

  public static boolean isIncorrectRegionOrAccelerationException(@NotNull Throwable s3Exception) {
    if (TRANSFER_ACC_ERROR_PATTERN.matcher(s3Exception.getMessage()).matches()) return true;
    return extractCorrectedRegion(s3Exception) != null;
  }

  private Map<String, String> extractCorrectedSettings(@NotNull String projectId, @NotNull Map<String, String> settings, @NotNull Throwable s3Exception)
    throws ConnectionCredentialsException, AmazonS3Exception {
    return buildAndCacheCorrectedSettings(projectId, settings, s3Exception);
  }

  @NotNull
  private Map<String, String> buildAndCacheCorrectedSettings(@NotNull String projectId,
                                                             @NotNull Map<String, String> settings,
                                                             @NotNull Throwable s3Exception) throws ConnectionCredentialsException {
    final Map<String, String> correctedSettings = buildCorrectedSettings(projectId, s3Exception);
    final String featureId = settings.get(ArtifactStorageSettings.STORAGE_FEATURE_ID);
    if (StringUtils.isNotBlank(featureId) && StringUtils.isNotBlank(projectId)) {
      myCorrectedSettings.put(new Pair<>(projectId, featureId), correctedSettings);
    }
    return correctedSettings;
  }

  @NotNull
  private static Map<String, String> buildCorrectedSettings(@NotNull String projectId,
                                                            @NotNull Throwable s3Exception) throws ConnectionCredentialsException {
    final HashMap<String, String> correctedSettings = new HashMap<>();
    final String correctedRegion = extractCorrectedRegion(s3Exception);
    final boolean isTAException = TRANSFER_ACC_ERROR_PATTERN.matcher(s3Exception.getMessage()).matches();
    final boolean isRegionException = correctedRegion != null;
    if (isTAException) {
      Loggers.CLOUD.debug(() -> "Running operation with disabled Transfer Acceleration for project " + projectId, s3Exception);
      correctedSettings.put(S3_ENABLE_ACCELERATE_MODE, "false");
      return correctedSettings;
    } else if (isRegionException) {
      Loggers.CLOUD.debug(() -> "Running operation with corrected S3 region [" + correctedRegion + "] for project " + projectId, s3Exception);
      correctedSettings.put(AWSCommonParams.REGION_NAME_PARAM, correctedRegion);
      return correctedSettings;
    } else if (s3Exception instanceof ConnectionCredentialsException) { // Should never happen
      throw (ConnectionCredentialsException)s3Exception;
    } else if (s3Exception instanceof AmazonS3Exception) {
      throw (AmazonS3Exception)s3Exception;
    } else {
      throw new RuntimeException("Cannot extract corrected settings from exception for project " + projectId, s3Exception);
    }
  }

  @Override
  public <T, E extends Exception> T withCloudFrontClient(@NotNull final Map<String, String> params,
                                                         @NotNull final String projectId,
                                                         @NotNull final WithCloudFrontClient<T, E> withClient)
    throws E, ConnectionCredentialsException {
    return withCloudFrontClient(params, projectId, withClient, false);
  }

  @Override
  public Map<String, String> correctRegionAndAcceleration(@NotNull final String bucketName,
                                                          @NotNull final Map<String, String> storageSettings,
                                                          @NotNull final String projectId) {
    if (TeamCityProperties.getBooleanOrTrue("teamcity.internal.storage.s3.autoCorrectRegion")) {
      return IOGuard.allowNetworkCall(() -> {
        try {
          return getCorrectedRegionAndAcceleration(bucketName, storageSettings, projectId);
        } catch (Throwable t) {
          throw new RuntimeException(t);
        }
      });
    }

    return storageSettings;
  }

  @NotNull
  private Map<String, String> getCorrectedRegionAndAcceleration(@NotNull String bucketName,
                                                                @NotNull Map<String, String> storageSettings,
                                                                @NotNull String projectId) throws ConnectionCredentialsException {
    final Map<String, String> cachedSettings = myCorrectedSettings.getIfPresent(new Pair<>(projectId, storageSettings.get(ArtifactStorageSettings.STORAGE_FEATURE_ID)));
    if (cachedSettings != null) {
      final Map<String, String> correctedSettings = new HashMap<>(storageSettings);
      correctedSettings.putAll(cachedSettings);
      return correctedSettings;
    }

    // No call has been made to S3, we make one to verify the quality of the settings 
    withS3ClientShuttingDownImmediately(storageSettings, projectId, client -> getRegionName(client.getBucketLocation(bucketName)));

    return extractCachedCorrectedSettings(storageSettings, projectId);
  }

  @NotNull
  @Override
  public AmazonS3 fromS3Configuration(@NotNull final Map<String, String> s3Settings,
                                      @NotNull final String projectId,
                                      @Nullable final S3Util.S3AdvancedConfiguration advancedConfiguration)
    throws ConnectionCredentialsException {
    //TODO clarify when this should be used:
    S3Util.S3AdvancedConfiguration configuration = advancedConfiguration != null ? advancedConfiguration : S3Util.S3AdvancedConfiguration.defaultConfiguration();

    ClientConfiguration s3ClientConfig = getClientConfiguration(s3Settings);
    AwsConnectionCredentials awsConnectionCredentials = getAwsConnectionCredentials(s3Settings, projectId);

    String regionName = awsConnectionCredentials.getAwsRegion();
    if (StringUtils.isNotBlank(s3Settings.get(AWSCommonParams.REGION_NAME_PARAM))) {
      regionName = s3Settings.get(AWSCommonParams.REGION_NAME_PARAM);
    }

    return AmazonS3ClientBuilder
      .standard()
      .withRegion(regionName)
      .withClientConfiguration(s3ClientConfig)
      .withCredentials(awsConnectionCredentials.toAWSCredentialsProvider())
      .withAccelerateModeEnabled(isAccelerateModeEnabled(s3Settings))
      .withPathStyleAccessEnabled(!disablePathStyleAccess(s3Settings))
      .build();
  }

  private <T, E extends Exception> T withCloudFrontClient(@NotNull final Map<String, String> params,
                                                          @NotNull final String projectId,
                                                          @NotNull final WithCloudFrontClient<T, E> withClient,
                                                          final boolean shutdownImmediately) throws E, ConnectionCredentialsException {
    if (ParamUtil.withAwsConnectionId(params)) {
      AwsConnectionCredentials awsConnectionCredentials = getAwsConnectionCredentials(params, projectId);

      final AmazonCloudFront client = AmazonCloudFrontClientBuilder
        .standard()
        .withRegion(awsConnectionCredentials.getAwsRegion())
        .withClientConfiguration(ClientConfigurationBuilder.createClientConfigurationEx("cloudFront"))
        .withCredentials(awsConnectionCredentials.toAWSCredentialsProvider())
        .build();
      try {
        return withClient.execute(client);
      } finally {
        if (shutdownImmediately) {
          jetbrains.buildServer.util.amazon.S3Util.shutdownClient(client);
        }
      }
    } else {
      try {
        return AWSCommonParams.withAWSClients(params, clients -> {
          clients.setS3SignerType(S3_SIGNER_TYPE);
          clients.setDisablePathStyleAccess(disablePathStyleAccess(params));
          clients.setAccelerateModeEnabled(isAccelerateModeEnabled(params));
          patchAWSClientsSsl(clients, params);
          final AmazonCloudFront client = clients.createCloudFrontClient();
          try {
            return withClient.execute(client);
          } finally {
            if (shutdownImmediately) {
              jetbrains.buildServer.util.amazon.S3Util.shutdownClient(client);
            }
          }
        });
      } catch (Throwable t) {
        throw new ConnectionCredentialsException(new Exception(t));
      }
    }
  }

  private <T, E extends Exception> T withS3Client(@NotNull final Map<String, String> params,
                                                  @NotNull final String projectId,
                                                  @NotNull final WithS3Client<T, E> withS3Client, boolean shutdownImmediately) throws ConnectionCredentialsException {

    final Map<String, String> correctedSettings = extractCachedCorrectedSettings(params, projectId);
    if (ParamUtil.withAwsConnectionId(correctedSettings)) {
      AmazonS3 s3Client = fromS3Settings(correctedSettings, projectId);
      try {
        return withS3Client.execute(s3Client);
      } catch (Exception e) {
        throw new ConnectionCredentialsException(e);
      } finally {
        if (shutdownImmediately) {
          shutdownClient(s3Client);
        }
      }
    } else {
      try {
        return AWSCommonParams.withAWSClients(correctedSettings, clients -> {
          clients.setS3SignerType(S3_SIGNER_TYPE);
          clients.setDisablePathStyleAccess(disablePathStyleAccess(correctedSettings));
          clients.setAccelerateModeEnabled(isAccelerateModeEnabled(correctedSettings));
          patchAWSClientsSsl(clients, correctedSettings);
          final AmazonS3 s3Client = clients.createS3Client();
          try {
            return withS3Client.execute(s3Client);
          } finally {
            if (shutdownImmediately) {
              jetbrains.buildServer.util.amazon.S3Util.shutdownClient(s3Client);
            }
          }
        });
      } catch (Throwable t) {
        throw new ConnectionCredentialsException(new Exception(t));
      }
    }
  }

  private Map<String, String> extractCachedCorrectedSettings(@NotNull final Map<String, String> params,
                                                             @NotNull final String projectId) {
    final String featureId = params.get(ArtifactStorageSettings.STORAGE_FEATURE_ID);
    if (StringUtils.isBlank(featureId) || StringUtils.isBlank(projectId)) {
      return params;
    }

    final Map<String, String> cachedSettings;
    try {
      cachedSettings = myCorrectedSettings.get(new Pair<>(projectId, featureId), () -> Collections.emptyMap());
    } catch (ExecutionException e) {
      return params;
    }

    if (cachedSettings.isEmpty()) {
      return params;
    }

    final Map<String, String> correctedSettings = new HashMap<>(params);
    correctedSettings.putAll(cachedSettings);
    return correctedSettings;
  }

  @NotNull
  private ClientConfiguration getClientConfiguration(@NotNull Map<String, String> params) {
    final ConnectionSocketFactory socketFactory = new CachingSocketFactory(SystemTimeService.getInstance()).socketFactory(params.get(SSL_CERT_DIRECTORY_PARAM));
    ClientConfiguration s3ClientConfig = ClientConfigurationBuilder.createClientConfigurationEx("s3", socketFactory);
    //TODO Can we just always use the V4 signer type?
    s3ClientConfig.withSignerOverride(S3_SIGNER_TYPE);
    return s3ClientConfig;
  }

  @NotNull
  private AwsConnectionCredentials getAwsConnectionCredentials(@NotNull Map<String, String> params, @NotNull String projectId)
    throws ConnectionCredentialsException {
    SProject project = myProjectManager.findProjectById(projectId);
    if (project == null) {
      project = myProjectManager.findProjectByExternalId(projectId);
      if (project == null) {
        throw new ConnectionCredentialsException("Failed to find project with internal ID: " + projectId);
      }
    }

    ConnectionCredentials connectionCredentials = getConnectionCredentials(project, params);

    return new AwsConnectionCredentials(connectionCredentials);
  }

  @NotNull
  private ConnectionCredentials getConnectionCredentials(@NotNull SProject project, @NotNull Map<String, String> s3Settings) throws ConnectionCredentialsException {
    String linkedAwsConnectionId = ParamUtil.getLinkedAwsConnectionId(s3Settings);
    if (linkedAwsConnectionId == null) {
      throw new ConnectionCredentialsException("There is no linked AWS Connection to use for the S3 storage");
    }

    final Map<String, String> additionalParameters = new HashMap<String, String>();
    String storageFeatureId = s3Settings.get(ArtifactStorageSettings.STORAGE_FEATURE_ID);
    if (storageFeatureId != null) {
      additionalParameters.put(ArtifactStorageSettings.STORAGE_FEATURE_ID, storageFeatureId);
    }

    // TW-94823 Subprojects might use this connection and require access to the credentials
    additionalParameters.put(AwsCloudConnectorConstants.ALLOWED_IN_SUBPROJECTS_PARAM, "true");

    return myProjectConnectionCredentialsManager.requestConnectionCredentials(project, linkedAwsConnectionId, additionalParameters);
  }
}
