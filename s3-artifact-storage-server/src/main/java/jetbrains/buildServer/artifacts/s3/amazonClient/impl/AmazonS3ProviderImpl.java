package jetbrains.buildServer.artifacts.s3.amazonClient.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import com.intellij.openapi.util.Pair;
import java.util.concurrent.ExecutionException;
import jetbrains.buildServer.artifacts.ArtifactStorageSettings;
import jetbrains.buildServer.artifacts.s3.CachingSocketFactory;
import jetbrains.buildServer.artifacts.s3.amazonClient.AmazonS3Provider;
import jetbrains.buildServer.artifacts.s3.amazonClient.WithCloudFrontClient;
import jetbrains.buildServer.artifacts.s3.amazonClient.WithS3Client;
import jetbrains.buildServer.artifacts.s3.amazonClient.WithS3Presigner;
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
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.signer.AwsS3V4Signer;
import software.amazon.awssdk.awscore.defaultsmode.DefaultsMode;
import software.amazon.awssdk.core.client.config.SdkAdvancedClientOption;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_ENABLE_ACCELERATE_MODE;
import static jetbrains.buildServer.artifacts.s3.S3Util.disablePathStyleAccess;
import static jetbrains.buildServer.artifacts.s3.S3Util.isAccelerateModeEnabled;
import static jetbrains.buildServer.artifacts.s3.S3Util.patchAWSClientsSsl;
import static jetbrains.buildServer.artifacts.s3.S3Util.TRANSFER_ACC_ERROR_PATTERN;
import static jetbrains.buildServer.clouds.amazon.connector.utils.parameters.AwsCommonParameters.SSL_CERT_DIRECTORY_PARAM;

public class AmazonS3ProviderImpl implements AmazonS3Provider {
  private final String S3_SIGNER_TYPE = "AWSS3V4SignerType";
  private final String CACHE_EXPIRATION_TIMEOUT = "teamcity.internal.storage.s3.correctSettingsCache.expirationTimeoutInHours";
  private final String ENABLE_CACHE = "teamcity.internal.storage.s3.correctSettingsCache.enable";

  private final ProjectManager myProjectManager;
  private final ProjectConnectionCredentialsManager myProjectConnectionCredentialsManager;
  private final Cache<Pair<String, String>, Map<String, String>> myCorrectedSettings = CacheBuilder.newBuilder()
                                                                                                   .expireAfterAccess(TeamCityProperties.getInteger(CACHE_EXPIRATION_TIMEOUT, 24)
                                                                                                     , java.util.concurrent.TimeUnit.HOURS)
                                                                                                   .build();

  public AmazonS3ProviderImpl(@NotNull final ProjectManager projectManager,
                              @NotNull final ProjectConnectionCredentialsManager projectConnectionCredentialsManager) {
    myProjectManager = projectManager;
    myProjectConnectionCredentialsManager = projectConnectionCredentialsManager;
  }

  @Nullable
  private static String extractCorrectedRegion(@NotNull final Throwable e) {
    @Nullable final S3Exception awsException = e instanceof S3Exception ? (S3Exception) e : ExceptionUtil.getCause(e, S3Exception.class);
    if (TeamCityProperties.getBooleanOrTrue("teamcity.internal.storage.s3.autoCorrectRegion")
      && awsException != null
      && awsException.awsErrorDetails() != null) {
      SdkHttpResponse sdkHttpResponse = awsException.awsErrorDetails().sdkHttpResponse();
      if (sdkHttpResponse != null) {
        return sdkHttpResponse.firstMatchingHeader("x-amz-bucket-region")
          .orElse(null);
      }
    }

    return null;
  }

  @NotNull
  private S3Client fromS3Settings(@NotNull final Map<String, String> s3Settings,
                                 @NotNull final String projectId) throws ConnectionCredentialsException {
    return fromS3Configuration(s3Settings, projectId, null);
  }

  @NotNull
  private S3Presigner presignerFromS3Settings(@NotNull final Map<String, String> s3Settings,
                                       @NotNull final String projectId) throws ConnectionCredentialsException {
    AwsConnectionCredentials awsConnectionCredentials = getAwsConnectionCredentials(s3Settings, projectId);

    String regionName = awsConnectionCredentials.getAwsRegion();
    if (StringUtils.isNotBlank(s3Settings.get(AWSCommonParams.REGION_NAME_PARAM))) {
      regionName = s3Settings.get(AWSCommonParams.REGION_NAME_PARAM);
    }

    return S3Presigner.builder()
                      .region(Region.of(regionName))
                      .credentialsProvider(awsConnectionCredentials.toAWSCredentialsProvider())
                      .serviceConfiguration(
                        S3Configuration.builder()
                                       .accelerateModeEnabled(isAccelerateModeEnabled(s3Settings))
                                       .pathStyleAccessEnabled(!disablePathStyleAccess(s3Settings))
                                       .build()
                      )
                      .build();
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
  public void shutdownClient(@NotNull final S3Client s3Client) {
    try {
      s3Client.close();
    } catch (Exception e) {
      Loggers.CLOUD.warnAndDebugDetails("Shutting down s3 client " + s3Client + " failed.", e);
    }
  }

  @Override
  public void shutdownPresigner(@NotNull final S3Presigner s3Presigner) {
    try {
      s3Presigner.close();
    } catch (Exception e) {
      Loggers.CLOUD.warnAndDebugDetails("Shutting down s3 presigner " + s3Presigner + " failed.", e);
    }
  }

  @Override
  public <T, E extends Exception> T withCorrectingRegionAndAcceleration(@NotNull final Map<String, String> settings,
                                                                        @NotNull final String projectId,
                                                                        @NotNull final WithS3Client<T, E> action,
                                                                        final boolean shutdownImmediately) throws ConnectionCredentialsException {
    try {
      return withS3Client(settings, projectId, action, shutdownImmediately);
    } catch (S3Exception | ConnectionCredentialsException s3Exception) {
      final Map<String, String> correctedSettings = extractCorrectedSettings(projectId, settings, s3Exception);
      return withS3Client(correctedSettings, projectId, action, shutdownImmediately);
    }
  }

  public static boolean isIncorrectRegionOrAccelerationException(@NotNull Throwable s3Exception) {
    if (TRANSFER_ACC_ERROR_PATTERN.matcher(s3Exception.getMessage()).matches()) return true;
    return extractCorrectedRegion(s3Exception) != null;
  }

  private Map<String, String> extractCorrectedSettings(@NotNull String projectId, @NotNull Map<String, String> settings, @NotNull Throwable s3Exception)
    throws ConnectionCredentialsException, S3Exception {
    final Map<String, String> correctedSettings = new HashMap<>(settings);
    correctedSettings.putAll(buildAndCacheCorrectedSettings(projectId, settings, s3Exception));
    return correctedSettings;
  }

  @NotNull
  private Map<String, String> buildAndCacheCorrectedSettings(@NotNull String projectId,
                                                             @NotNull Map<String, String> settings,
                                                             @NotNull Throwable s3Exception) throws ConnectionCredentialsException {
    final Map<String, String> correctedSettings = buildCorrectedSettings(projectId, s3Exception);
    final String featureId = settings.get(ArtifactStorageSettings.STORAGE_FEATURE_ID);
    if (StringUtils.isNotBlank(featureId) && StringUtils.isNotBlank(projectId) && TeamCityProperties.getBooleanOrTrue(ENABLE_CACHE)) {
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
    } else if (s3Exception instanceof S3Exception) {
      throw (S3Exception) s3Exception;
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
    withS3ClientShuttingDownImmediately(storageSettings, projectId, client ->
      client.getBucketLocation(builder -> builder.bucket(bucketName))
    );

    return extractCachedCorrectedSettings(storageSettings, projectId);
  }

  @NotNull
  private S3Client fromS3Configuration(@NotNull final Map<String, String> s3Settings,
                                      @NotNull final String projectId,
                                      @Nullable final S3Util.S3AdvancedConfiguration advancedConfiguration)
    throws ConnectionCredentialsException {

    SdkHttpClient.Builder<ApacheHttpClient.Builder> clientConfiguration = getClientConfiguration(s3Settings);
    AwsConnectionCredentials awsConnectionCredentials = getAwsConnectionCredentials(s3Settings, projectId);

    String regionName = awsConnectionCredentials.getAwsRegion();
    if (StringUtils.isNotBlank(s3Settings.get(AWSCommonParams.REGION_NAME_PARAM))) {
      regionName = s3Settings.get(AWSCommonParams.REGION_NAME_PARAM);
    }

    return S3Client.builder()
      .defaultsMode(DefaultsMode.STANDARD)
      .region(Region.of(regionName))
      .httpClientBuilder(clientConfiguration)
      .overrideConfiguration(
        ClientConfigurationBuilder.clientOverrideConfigurationBuilder()
          .putAdvancedOption(SdkAdvancedClientOption.SIGNER, AwsS3V4Signer.create())
          .build()
      )
      .credentialsProvider(awsConnectionCredentials.toAWSCredentialsProvider())
      .serviceConfiguration(
        builder -> builder.accelerateModeEnabled(isAccelerateModeEnabled(s3Settings))
        .pathStyleAccessEnabled(!disablePathStyleAccess(s3Settings))
      )
      .build();
  }

  private <T, E extends Exception> T withCloudFrontClient(@NotNull final Map<String, String> params,
                                                          @NotNull final String projectId,
                                                          @NotNull final WithCloudFrontClient<T, E> withClient,
                                                          final boolean shutdownImmediately) throws E, ConnectionCredentialsException {
    if (ParamUtil.withAwsConnectionId(params)) {
      AwsConnectionCredentials awsConnectionCredentials = getAwsConnectionCredentials(params, projectId);

      final CloudFrontClient client = CloudFrontClient.builder()
        .defaultsMode(DefaultsMode.STANDARD)
        .region(Region.of(awsConnectionCredentials.getAwsRegion()))
        .httpClientBuilder(ClientConfigurationBuilder.createClientBuilder("cloudFront"))
        .overrideConfiguration(ClientConfigurationBuilder.clientOverrideConfigurationBuilder().build())
        .credentialsProvider(awsConnectionCredentials.toAWSCredentialsProvider())
        .build();
      try {
        return withClient.execute(client);
      } finally {
        if (shutdownImmediately) {
          S3Util.shutdownClient(client);
        }
      }
    } else {
      try {
        return AWSCommonParams.withAWSClients(params, clients -> {
          clients.setS3SignerType(S3_SIGNER_TYPE);
          clients.setDisablePathStyleAccess(disablePathStyleAccess(params));
          clients.setAccelerateModeEnabled(isAccelerateModeEnabled(params));
          patchAWSClientsSsl(clients, params);
          final CloudFrontClient client = clients.createCloudFrontClient();
          try {
            return withClient.execute(client);
          } finally {
            if (shutdownImmediately) {
              S3Util.shutdownClient(client);
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
      S3Client s3Client = fromS3Settings(correctedSettings, projectId);
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
        //todo shouldn't be merged unless this branch is supported
        //to think about of converging both paths (need to sep static creds vs session ones)
        //better to move it to this plugin instead of leaving it in the AWS Core
        return AWSCommonParams.withAWSClients(correctedSettings, clients -> {
          clients.setS3SignerType(S3_SIGNER_TYPE);
          clients.setDisablePathStyleAccess(disablePathStyleAccess(correctedSettings));
          clients.setAccelerateModeEnabled(isAccelerateModeEnabled(correctedSettings));
          patchAWSClientsSsl(clients, correctedSettings);
          final S3Client s3Client = clients.createS3Client();
          try {
            return withS3Client.execute(s3Client);
          } finally {
            if (shutdownImmediately) {
              S3Util.shutdownClient(s3Client);
            }
          }
        });
      } catch (Throwable t) {
        throw new ConnectionCredentialsException(new Exception(t));
      }
    }
  }

  @Override
  public  <T, E extends Exception> T withS3PresignerShuttingDownImmediately(@NotNull final String bucket,
                                                                            @NotNull final Map<String, String> params,
                                                                            @NotNull final String projectId,
                                                                            @NotNull final WithS3Presigner<T, E> withS3Presigner)
    throws ConnectionCredentialsException {
    final Map<String, String> correctedSettings = getCorrectedRegionAndAcceleration(bucket, params, projectId);
    if (ParamUtil.withAwsConnectionId(correctedSettings)) {
      S3Presigner s3Presigner = presignerFromS3Settings(correctedSettings, projectId);
      try {
        return withS3Presigner.execute(s3Presigner);
      } catch (Exception e) {
        throw new ConnectionCredentialsException(e);
      } finally {
        shutdownPresigner(s3Presigner);
      }
    } else {
      try {
        return AWSCommonParams.withAWSClients(correctedSettings, clients -> {
          final AwsCredentials credentials = clients.getCredentials();
          if (credentials == null) {
            throw new ConnectionCredentialsException("Cannot generate presigned url, no AWS credentials provided");
          }
          final S3Presigner s3Presigner = S3Presigner.builder()
                                                     .region(Region.of(clients.getRegion()))
                                                     .credentialsProvider(StaticCredentialsProvider.create(credentials))
                                                     .serviceConfiguration(
                                                       S3Configuration.builder()
                                                                      .accelerateModeEnabled(isAccelerateModeEnabled(correctedSettings))
                                                                      .pathStyleAccessEnabled(!disablePathStyleAccess(correctedSettings))
                                                                      .build()
                                                     )
                                                     .build();
          try {
            return withS3Presigner.execute(s3Presigner);
          } finally {
            shutdownPresigner(s3Presigner);
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
  private SdkHttpClient.Builder<ApacheHttpClient.Builder> getClientConfiguration(@NotNull Map<String, String> params) {
    final ConnectionSocketFactory socketFactory = new CachingSocketFactory(SystemTimeService.getInstance()).socketFactory(params.get(SSL_CERT_DIRECTORY_PARAM));
    return ClientConfigurationBuilder.createClientBuilder("s3", socketFactory);
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
