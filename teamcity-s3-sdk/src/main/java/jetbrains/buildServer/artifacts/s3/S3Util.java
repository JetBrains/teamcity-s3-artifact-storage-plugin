

package jetbrains.buildServer.artifacts.s3;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import java.io.File;
import java.io.IOException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.TrustManager;
import jetbrains.buildServer.artifacts.ArtifactListData;
import jetbrains.buildServer.artifacts.s3.cloudfront.CloudFrontConstants;
import jetbrains.buildServer.artifacts.s3.exceptions.InvalidSettingsException;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.*;
import jetbrains.buildServer.util.amazon.AWSClients;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import jetbrains.buildServer.util.ssl.SSLContextUtil;
import jetbrains.buildServer.util.ssl.TrustStoreIO;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.http.TlsTrustManagersProvider;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.transfer.s3.model.CompletedTransfer;
import software.amazon.awssdk.transfer.s3.model.Transfer;

import static jetbrains.buildServer.clouds.amazon.connector.utils.parameters.AwsCloudConnectorConstants.DEFAULT_CONNECTION_TIMEOUT;
import static jetbrains.buildServer.artifacts.s3.S3Constants.*;
import static jetbrains.buildServer.util.amazon.AWSCommonParams.SSL_CERT_DIRECTORY_PARAM;
import static jetbrains.buildServer.util.amazon.S3Util.*;

/**
 * Created by Nikita.Skvortsov
 * date: 02.08.2016.
 */
public final class S3Util {
  public static final Pattern TRANSFER_ACC_ERROR_PATTERN =
    Pattern.compile(".*S3 Transfer Acceleration is (not configured|disabled) on this bucket.*");
  public static final Pattern HEADER_MALFORMED_ERROR_PATTERN =
    Pattern.compile("The authorization header is malformed; the region \\'.+\\' is wrong; expecting \\'(.+)\\'");
  public static final String AUTHORIZATION_HEADER_MALFORMED_CODE = "AuthorizationHeaderMalformed";
  private static final Pattern BEGIN_MATCHER = Pattern.compile("^(-----BEGIN[\\w\\s]+-----)\\n?");
  private static final Pattern END_MATCHER = Pattern.compile("\\n?(-----END[\\w\\s]+-----)$");
  @NotNull
  private static final Pattern OUR_OBJECT_KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9!/\\-_.*'()]+$");
  private static final int OUT_MAX_PREFIX_LENGTH = 127;
  @NotNull
  private static final Logger LOGGER = Logger.getInstance(S3Util.class.getName());
  @NotNull
  public static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
  @NotNull
  public static final String DEFAULT_CHARSET = "charset=UTF-8";
  @NotNull
  private static final String V4_SIGNER_TYPE = "AWSS3V4SignerType";
  @NotNull
  private static final Map<String, String> CUSTOM_CONTENT_TYPES = CollectionsUtil.asMap("css", "text/css", "js", "application/javascript");
  @NotNull
  private static final String MULTIPART_UPLOAD_MIN_VALUE = "5MB";
  private static final long MULTIPART_UPLOAD_MIN_VALUE_IN_BYTES = StringUtil.parseFileSize(MULTIPART_UPLOAD_MIN_VALUE);
  @NotNull
  private static final CachingSocketFactory OUR_SOCKET_FACTORY = new CachingSocketFactory(SystemTimeService.getInstance());

  private S3Util() {
  }

  @NotNull
  public static Map<String, String> validateParameters(@NotNull final Map<String, String> params, final boolean acceptReferences) {
    final Map<String, String> invalids = new HashMap<>();
    if (StringUtil.isEmptyOrSpaces(getBucketName(params))) {
      invalids.put(beanPropertyNameForBucketName(), "S3 bucket name must not be empty");
    }
    final String pathPrefix = params.getOrDefault(S3_PATH_PREFIX_SETTING, "");
    if (!StringUtil.isEmptyOrSpaces(pathPrefix)) {
      if (pathPrefix.length() > OUT_MAX_PREFIX_LENGTH) {
        invalids.put(S3_PATH_PREFIX_SETTING, "Should be less than " + OUT_MAX_PREFIX_LENGTH + " characters");
      }
      if (!OUR_OBJECT_KEY_PATTERN.matcher(pathPrefix).matches()) {
        invalids.put(S3_PATH_PREFIX_SETTING, "Should match the regexp [" + OUR_OBJECT_KEY_PATTERN.pattern() + "]");
      }
    }
    final Pair<Long, String> partSizeValueWithError = parseMultipartUploadByteSetting(params.get(S3_MULTIPART_MINIMUM_UPLOAD_PART_SIZE));
    if (partSizeValueWithError.getSecond() != null) {
      invalids.put(S3_MULTIPART_MINIMUM_UPLOAD_PART_SIZE, "Invalid " + partSizeValueWithError.getSecond());
    }
    final Pair<Long, String> thresholdWithError = parseMultipartUploadByteSetting(params.get(S3_MULTIPART_UPLOAD_THRESHOLD));
    if (thresholdWithError.getSecond() != null) {
      invalids.put(S3_MULTIPART_UPLOAD_THRESHOLD, "Invalid " + thresholdWithError.getSecond());
    }

    if (!disablePathStyleAccess(params) && isAccelerateModeEnabled(params)) {
      invalids.put(S3_ENABLE_ACCELERATE_MODE, "Transfer Acceleration can only be used together with Virtual Host Addressing");
    }
    return invalids;
  }

  @NotNull
  public static Map<String, String> validateParameters(@NotNull final Map<String, String> params) throws IllegalArgumentException, InvalidSettingsException {
    final Map<String, String> invalids = validateParameters(params, false);
    if (!invalids.isEmpty()) {
      throw new InvalidSettingsException(invalids);
    }
    return params;
  }

  @NotNull
  public static String beanPropertyNameForBucketName() {
    return S3_BUCKET_NAME;
  }

  @Nullable
  public static String getBucketName(@NotNull final Map<String, String> params) {
    return params.get(beanPropertyNameForBucketName());
  }

  @NotNull
  public static String getBucketRegion(@NotNull final Map<String, String> params) {
    return params.get(AWSCommonParams.REGION_NAME_PARAM);
  }

  public static boolean getCloudFrontEnabled(@NotNull final Map<String, String> params) {
    return Boolean.parseBoolean(params.get(CloudFrontConstants.S3_CLOUDFRONT_ENABLED));
  }

  @Nullable
  public static String getCloudFrontUploadDistribution(@NotNull final Map<String, String> params) {
    return params.get(CloudFrontConstants.S3_CLOUDFRONT_UPLOAD_DISTRIBUTION);
  }

  @Nullable
  public static String getCloudFrontDownloadDistribution(@NotNull final Map<String, String> params) {
    return params.get(CloudFrontConstants.S3_CLOUDFRONT_DOWNLOAD_DISTRIBUTION);
  }

  @Nullable
  public static String getCloudFrontPrivateKey(@NotNull final Map<String, String> params) {
    String cloudFrontPrivateKey = params.get(CloudFrontConstants.S3_CLOUDFRONT_PRIVATE_KEY);

    if (cloudFrontPrivateKey != null) {
      return END_MATCHER.matcher(BEGIN_MATCHER.matcher(cloudFrontPrivateKey).replaceAll("$1\n")).replaceAll("\n$1");
    } else {
      return null;
    }
  }

  @Nullable
  public static String getCloudFrontPublicKeyId(@NotNull final Map<String, String> params) {
    return params.get(CloudFrontConstants.S3_CLOUDFRONT_PUBLIC_KEY_ID);
  }

  @Nullable
  public static String getPathPrefix(@NotNull final ArtifactListData artifactsInfo) {
    return getPathPrefix(artifactsInfo.getCommonProperties());
  }

  @Nullable
  public static String getPathPrefix(@NotNull final Map<String, String> properties) {
    return properties.get(S3Constants.S3_PATH_PREFIX_ATTR);
  }

  public static boolean usePreSignedUrls(@NotNull final Map<String, String> properties) {
    return Boolean.parseBoolean(properties.get(S3Constants.S3_USE_PRE_SIGNED_URL_FOR_UPLOAD));
  }

  public static int getNumberOfRetries(@NotNull final Map<String, String> configurationParameters) {
    return getIntegerConfigurationParameterOrDefault(configurationParameters, S3_NUMBER_OF_RETRIES_ON_ERROR, DEFAULT_NUMBER_OF_RETRIES_ON_ERROR);
  }

  public static int getRetryDelayInMs(@NotNull final Map<String, String> configurationParameters) {
    return getIntegerConfigurationParameterOrDefault(configurationParameters, S3_RETRY_DELAY_ON_ERROR_MS, DEFAULT_RETRY_DELAY_ON_ERROR_MS);
  }

  private static int getIntegerConfigurationParameterOrDefault(@NotNull final Map<String, String> configurationParameters,
                                                               @NotNull final String parameterName,
                                                               final int defaultValue) {
    final String strValue = configurationParameters.get(parameterName);
    try {
      if (StringUtil.isNotEmpty(strValue)) {
        final int intValue = Integer.parseInt(strValue);
        if (intValue >= 0) {
          return intValue;
        }
      }
    } catch (NumberFormatException e) {
      LOGGER.debug(() -> "Cannot parse '" + parameterName + "=" + strValue + "'", e);
    }
    LOGGER.debug(() -> "Using default value '" + defaultValue + "' for property '" + parameterName + "'");
    return defaultValue;
  }

  public static int getMaxNumberOfPresignedUrlsToLoadInOneRequest(@NotNull final Map<String, String> configurationParameters) {
    try {
      final int nRetries = Integer.parseInt(configurationParameters.get(S3_PRESIGNED_UPLOAD_MAX_NUMBER_OF_URLS_TO_LOAD_IN_ONE_REQUEST));
      return nRetries >= 0 ? nRetries : DEFAULT_PRESIGNED_URL_MAX_CHUNK_SIZE;
    } catch (NumberFormatException e) {
      return DEFAULT_PRESIGNED_URL_MAX_CHUNK_SIZE;
    }
  }

  @Nullable
  public static Long getMultipartUploadThreshold(Map<String, String> sharedConfigurationParameters,
                                                 @NotNull final Map<String, String> artifactStorageSettings) {
    final String stringValue = artifactStorageSettings.getOrDefault(S3_MULTIPART_UPLOAD_THRESHOLD, sharedConfigurationParameters.get(S3_MULTIPART_UPLOAD_THRESHOLD));
    final Pair<Long, String> valueOrError = parseMultipartUploadByteSetting(stringValue);
    if (valueOrError.getSecond() != null) {
      LOGGER.warn("Invalid " + S3_MULTIPART_UPLOAD_THRESHOLD + ": " + valueOrError.getSecond() + ". The default value will be used");
    }
    return valueOrError.getFirst();
  }

  @Nullable
  public static Long getMinimumUploadPartSize(Map<String, String> sharedConfigurationParameters,
                                              @NotNull final Map<String, String> artifactStorageSettings) {
    final String stringValue =
      artifactStorageSettings.getOrDefault(S3_MULTIPART_MINIMUM_UPLOAD_PART_SIZE, sharedConfigurationParameters.get(S3_MULTIPART_MINIMUM_UPLOAD_PART_SIZE));
    if (stringValue == null) {
      return null;
    }
    final Pair<Long, String> valueOrError = parseMultipartUploadByteSetting(stringValue);
    if (valueOrError.getSecond() != null) {
      LOGGER.warn("Invalid " + S3_MULTIPART_MINIMUM_UPLOAD_PART_SIZE + ": " + valueOrError.getSecond() + ". The default value will be used");
    }
    return valueOrError.getFirst();
  }

  @NotNull
  private static Pair<Long, String> parseMultipartUploadByteSetting(@Nullable String value) {
    if (StringUtil.isEmpty(value)) {
      return Pair.create(null, null);
    }
    try {
      final long byteSetting = StringUtil.parseFileSize(value);
      if (byteSetting < MULTIPART_UPLOAD_MIN_VALUE_IN_BYTES) {
        return Pair.create(null, "[" + value + "], should be at least " + MULTIPART_UPLOAD_MIN_VALUE);
      } else {
        return Pair.create(byteSetting, null);
      }
    } catch (NumberFormatException e) {
      return Pair.create(null, "[" + value + "], should be integer");
    }
  }

  public static boolean getPresignedMultipartUploadEnabled(@NotNull final Map<String, String> configuration) {
    return Boolean.parseBoolean(configuration.getOrDefault(S3_ENABLE_PRESIGNED_MULTIPART_UPLOAD, "true"));
  }

  public static int getConnectionTimeout(@NotNull final Map<String, String> configuration) {
    return Integer.parseInt(configuration.getOrDefault(S3_CONNECTION_TIMEOUT, String.valueOf(TeamCityProperties.getInteger(S3_CONNECTION_TIMEOUT, DEFAULT_CONNECTION_TIMEOUT))));
  }

  public static int getNumberOfThreadsForFiles(@NotNull final Map<String, String> configuration) {
    return Integer.parseInt(
      configuration.getOrDefault(S3_NUMBER_OF_THREADS_FOR_FILES, String.valueOf(TeamCityProperties.getInteger(TRANSFER_MANAGER_THREAD_POOL_SIZE, DEFAULT_S3_THREAD_POOL_SIZE))));
  }

  public static int getNumberOfThreadsForFileParts(@NotNull final Map<String, String> configuration) {
    return Integer.parseInt(
      configuration.getOrDefault(S3_NUMBER_OF_THREADS_FOR_FILE_PARTS, String.valueOf(TeamCityProperties.getInteger(TRANSFER_MANAGER_THREAD_POOL_SIZE, DEFAULT_S3_THREAD_POOL_SIZE))));
  }

  public static int getUrlTtlSeconds(@NotNull final Map<String, String> configuration) {
    return Integer.parseInt(configuration.getOrDefault(S3_URL_LIFETIME_SEC, String.valueOf(TeamCityProperties.getInteger(S3_URL_LIFETIME_SEC, DEFAULT_URL_LIFETIME_SEC))));
  }

  public static int getUrlExtendedTtlSeconds(@NotNull final Map<String, String> configuration) {
    String urlExtendedTtlSeconds = configuration.getOrDefault(S3_URL_LIFETIME_EXTENDED_SEC, TeamCityProperties.getPropertyOrNull(S3_URL_LIFETIME_EXTENDED_SEC));
    if (urlExtendedTtlSeconds != null) {
      return Integer.parseInt(urlExtendedTtlSeconds.trim());
    }
    return getUrlTtlSeconds(configuration);
  }

  public static boolean isConsistencyCheckEnabled(Map<String, String> storageSettings) {
    return Boolean.parseBoolean(TeamCityProperties.getProperty(S3_ENABLE_CONSISTENCY_CHECK, String.valueOf(DEFAULT_ENABLE_CONSISTENCY_CHECK))) &&
           Boolean.parseBoolean(storageSettings.getOrDefault(S3_VERIFY_INTEGRITY_AFTER_UPLOAD, "true"));
  }

  @NotNull
  public static ObjectCannedACL getAcl(@NotNull final Map<String, String> configuration, Map<String, String> projectConfiguration) {
    final String acl = projectConfiguration.getOrDefault(S3_ACL, configuration.getOrDefault(S3_ACL, ObjectCannedACL.BUCKET_OWNER_FULL_CONTROL.name()));
    return Arrays.stream(ObjectCannedACL.values())
                 .filter(v -> v.toString().equals(acl) || v.name().equals(acl))
                 .findFirst()
                 .orElseGet(() -> {
                   LOGGER.warn(String.format("Unknown ACL provided: %s. The following ACL will be used: %s", acl, ObjectCannedACL.BUCKET_OWNER_FULL_CONTROL));
                   return ObjectCannedACL.BUCKET_OWNER_FULL_CONTROL;
                 });
  }

  public static boolean disablePathStyleAccess(@NotNull final Map<String, String> properties) {
    return Boolean.parseBoolean(properties.get(S3_FORCE_VIRTUAL_HOST_ADDRESSING));
  }

  public static boolean isAccelerateModeEnabled(@NotNull final Map<String, String> properties) {
    return TeamCityProperties.getBooleanOrTrue(S3_TRANSFER_ACCELERATION_FEATURE_ENABLED) && Boolean.parseBoolean(properties.get(S3_ENABLE_ACCELERATE_MODE));
  }

  @Deprecated
  public static <T, E extends Throwable> T withS3ClientShuttingDownImmediately(@NotNull final Map<String, String> params, @NotNull final WithS3<T, E> withClient) throws E {
    return withS3Client(params, withClient, true);
  }

  @Deprecated
  public static <T, E extends Throwable> T withS3Client(@NotNull final Map<String, String> params, @NotNull final WithS3<T, E> withClient) throws E {
    return withS3Client(params, withClient, false);
  }

  @Deprecated
  public static <T, E extends Throwable> T withCloudFrontClient(@NotNull final Map<String, String> params, @NotNull final WithCloudFront<T, E> withClient) throws E {
    return withCloudFrontClient(params, withClient, false);
  }

  @Deprecated
  private static <T, E extends Throwable> T withCloudFrontClient(@NotNull final Map<String, String> params,
                                                                 @NotNull final WithCloudFront<T, E> withClient,
                                                                 boolean shutdownImmediately) throws E {
    return AWSCommonParams.withAWSClients(params, clients -> {
      clients.setS3SignerType(V4_SIGNER_TYPE);
      clients.setDisablePathStyleAccess(disablePathStyleAccess(params));
      clients.setAccelerateModeEnabled(isAccelerateModeEnabled(params));
      patchAWSClientsSsl(clients, params);
      final CloudFrontClient client = clients.createCloudFrontClient();
      try {
        return withClient.run(client);
      } finally {
        if (shutdownImmediately) {
          jetbrains.buildServer.util.amazon.S3Util.shutdownClient(client);
        }
      }
    });
  }

  @Deprecated
  private static <T, E extends Throwable> T withS3Client(@NotNull final Map<String, String> params,
                                                         @NotNull final WithS3<T, E> withClient,
                                                         boolean shutdownImmediately) throws E {
    return AWSCommonParams.withAWSClients(params, clients -> {
      clients.setS3SignerType(V4_SIGNER_TYPE);
      clients.setDisablePathStyleAccess(disablePathStyleAccess(params));
      clients.setAccelerateModeEnabled(isAccelerateModeEnabled(params));
      patchAWSClientsSsl(clients, params);
      final S3Client s3Client = clients.createS3Client();
      try {
        return withClient.run(s3Client);
      } finally {
        if (shutdownImmediately) {
          jetbrains.buildServer.util.amazon.S3Util.shutdownClient(s3Client);
        }
      }
    });
  }

  @Deprecated
  public static <T extends Transfer> Collection<CompletedTransfer> withTransferManagerCorrectingRegion(@NotNull final Map<String, String> s3Settings,
                                                                                       @NotNull final WithTransferManager<T> withTransferManager,
                                                                                       @NotNull final S3AdvancedConfiguration advancedConfiguration)
    throws Throwable {
    try {
      return withTransferManager(s3Settings, withTransferManager, advancedConfiguration);
    } catch (RuntimeException e) {
      final String correctRegion = extractCorrectedRegion(e);
      if (correctRegion != null) {
        LOGGER.debug("Running operation with corrected S3 region [" + correctRegion + "]", e);
        s3Settings.put(AWSCommonParams.REGION_NAME_PARAM, correctRegion);
        return withTransferManager(s3Settings, withTransferManager, advancedConfiguration);
      } else {
        throw e;
      }
    }
  }

  @Deprecated
  private static <T extends Transfer> Collection<CompletedTransfer> withTransferManager(@NotNull final Map<String, String> s3Settings,
                                                                                        @NotNull final WithTransferManager<T> withTransferManager,
                                                                                        @Nullable final S3AdvancedConfiguration advancedConfiguration)
    throws Throwable {
    final S3AdvancedConfiguration configuration = advancedConfiguration != null ? advancedConfiguration : S3AdvancedConfiguration.defaultConfiguration();
    return AWSCommonParams.withAWSClients(s3Settings, clients -> {
      return jetbrains.buildServer.util.amazon.S3Util.withTransferManager(clients.createS3AsyncClient(configuration, createTrustManagerProvider(s3Settings)), withTransferManager, configuration);
    });
  }

  private static TlsTrustManagersProvider createTrustManagerProvider(@NotNull final Map<String, String> s3Settings) throws NoSuchAlgorithmException, KeyStoreException {
    String certDirectory = s3Settings.get(SSL_CERT_DIRECTORY_PARAM);
    if (certDirectory != null) {
      KeyStore ks = TrustStoreIO.readTrustStoreFromDirectory(certDirectory);
      TrustManager[] trustManagers = { SSLContextUtil.createTrustManager(ks) };
      return () -> trustManagers;
    }
    return null;
  }

  @Deprecated
  public static void patchAWSClientsSsl(@NotNull final AWSClients clients, @NotNull final Map<String, String> params) {
    final ConnectionSocketFactory socketFactory = OUR_SOCKET_FACTORY.socketFactory(params.get(SSL_CERT_DIRECTORY_PARAM));
    if (socketFactory != null) {
      clients.setSocketFactory(socketFactory);
    }
  }

  public static String getContentType(final File file) {
    final String customContentType = CUSTOM_CONTENT_TYPES.get(FileUtil.getExtension(file.getName()));
    if (customContentType != null) {
      return customContentType;
    }
    String contentType = URLConnection.guessContentTypeFromName(file.getName());
    if (StringUtil.isNotEmpty(contentType)) {
      return contentType;
    } else {
      try {
        contentType = Files.probeContentType(file.toPath());
        if (StringUtil.isNotEmpty(contentType)) {
          return contentType;
        }
      } catch (IOException ignore) {
      }
      return DEFAULT_CONTENT_TYPE;
    }
  }

  public static String normalizeArtifactPath(final String path, final File file) {
    if (StringUtil.isEmpty(path)) {
      return file.getName();
    } else {
      return FileUtil.normalizeRelativePath(String.format("%s/%s", path, file.getName()));
    }
  }

  @Deprecated
  public static <T> T withCorrectingRegionAndAcceleration(@NotNull final Map<String, String> settings,
                                                          @NotNull final WithS3<T, S3Exception> action) {
    try {
      return withS3ClientShuttingDownImmediately(settings, action);
    } catch (S3Exception s3Exception) {
      final String correctedRegion = extractCorrectedRegion(s3Exception);
      final boolean isTAException = TRANSFER_ACC_ERROR_PATTERN.matcher(s3Exception.awsErrorDetails().errorMessage()).matches();
      final boolean isRegionException = correctedRegion != null;

      final HashMap<String, String> correctedSettings = new HashMap<>(settings);

      if (isTAException) {
        LOGGER.debug("Running operation with disabled Transfer Acceleration", s3Exception);
        correctedSettings.put(S3_ENABLE_ACCELERATE_MODE, "false");
        return withCorrectingRegionAndAcceleration(correctedSettings, action);
      } else if (isRegionException) {
        LOGGER.debug("Running operation with corrected S3 region [" + correctedRegion + "]", s3Exception);
        correctedSettings.put(AWSCommonParams.REGION_NAME_PARAM, correctedRegion);
        return withCorrectingRegionAndAcceleration(correctedSettings, action);
      } else {
        throw s3Exception;
      }
    }
  }

  @Deprecated
  public static <T> T withClientCorrectingRegion(@NotNull final S3Client s3Client,
                                                 @NotNull final Map<String, String> settings,
                                                 @NotNull final WithS3<T, S3Exception> withCorrectedClient) {
    try {
      return withCorrectedClient.run(s3Client);
    } catch (S3Exception awsException) {
      final String correctRegion = extractCorrectedRegion(awsException);
      if (correctRegion != null) {
        LOGGER.debug("Running operation with corrected S3 region [" + correctRegion + "]", awsException);
        final HashMap<String, String> correctedSettings = new HashMap<>(settings);
        correctedSettings.put(AWSCommonParams.REGION_NAME_PARAM, correctRegion);
        return withS3ClientShuttingDownImmediately(correctedSettings, withCorrectedClient);
      } else {
        throw awsException;
      }
    }
  }

  @Deprecated
  @Nullable
  private static String extractCorrectedRegion(@NotNull final Throwable e) {
    @Nullable final S3Exception awsException = e instanceof S3Exception ? (S3Exception)e : ExceptionUtil.getCause(e, S3Exception.class);
    if (awsException != null && TeamCityProperties.getBooleanOrTrue("teamcity.internal.storage.s3.autoCorrectRegion") && awsException.awsErrorDetails() != null) {
      final SdkHttpResponse response = awsException.awsErrorDetails().sdkHttpResponse();
      if (response != null) {
        return response.firstMatchingHeader("x-amz-bucket-region").orElseGet(() -> extractRegionIfMalformedHeaderException(awsException));
      }
    }
    return null;
  }

  public static boolean isAllowPlainHttpUpload(Map<String, String> params) {
    if (params.containsKey(ALLOW_HTTP_CONNECTION_FOR_UPLOAD)) {
      return Boolean.parseBoolean(params.get(ALLOW_HTTP_CONNECTION_FOR_UPLOAD));
    }

    return TeamCityProperties.getBoolean(ALLOW_HTTP_CONNECTION_FOR_UPLOAD, false);
  }

  public static String extractRegionIfMalformedHeaderException(S3Exception awsException) {
    if (!AUTHORIZATION_HEADER_MALFORMED_CODE.equals(awsException.awsErrorDetails().errorCode())) {
      return null;
    }
    Matcher m = HEADER_MALFORMED_ERROR_PATTERN.matcher(awsException.awsErrorDetails().errorMessage());
    return m.matches() ? m.group(1) : null;
  }

  @Deprecated
  public interface WithS3<T, E extends Throwable> {
    @Nullable
    T run(@NotNull S3Client client) throws E;
  }

  @Deprecated
  public interface WithCloudFront<T, E extends Throwable> {
    @Nullable
    T run(@NotNull CloudFrontClient client) throws E;
  }

}
