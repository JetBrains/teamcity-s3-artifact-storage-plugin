/*
 * Copyright 2000-2021 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.artifacts.s3;

import com.amazonaws.services.cloudfront.AmazonCloudFront;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.transfer.Transfer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import java.io.File;
import java.io.IOException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import jetbrains.buildServer.artifacts.ArtifactListData;
import jetbrains.buildServer.artifacts.s3.cloudfront.CloudFrontConstants;
import jetbrains.buildServer.artifacts.s3.exceptions.InvalidSettingsException;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.*;
import jetbrains.buildServer.util.amazon.AWSClients;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.amazonaws.ClientConfiguration.DEFAULT_CONNECTION_TIMEOUT;
import static jetbrains.buildServer.artifacts.s3.S3Constants.*;
import static jetbrains.buildServer.util.amazon.AWSCommonParams.REGION_NAME_PARAM;
import static jetbrains.buildServer.util.amazon.AWSCommonParams.SSL_CERT_DIRECTORY_PARAM;
import static jetbrains.buildServer.util.amazon.S3Util.*;

/**
 * Created by Nikita.Skvortsov
 * date: 02.08.2016.
 */
public final class S3Util {
  @NotNull
  private static final Pattern OUR_OBJECT_KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9!/\\-_.*'()]+$");
  private static final int OUT_MAX_PREFIX_LENGTH = 127;
  @NotNull
  private static final Logger LOGGER = Logger.getInstance(S3Util.class.getName());
  @NotNull
  private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
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
    final Map<String, String> commonErrors = AWSCommonParams.validate(params, acceptReferences);
    if (!commonErrors.isEmpty()) {
      return commonErrors;
    }
    final Map<String, String> invalids = new HashMap<>();
    if (StringUtil.isEmptyOrSpaces(getBucketName(params))) {
      invalids.put(beanPropertyNameForBucketName(), "S3 bucket name must not be empty");
    }
    final String pathPrefix = params.getOrDefault(S3_PATH_PREFIX_SETTING, "");
    if (pathPrefix.length() > OUT_MAX_PREFIX_LENGTH) {
      invalids.put(S3_PATH_PREFIX_SETTING, "Should be less than " + OUT_MAX_PREFIX_LENGTH + " characters");
    }
    final String acl = params.getOrDefault(S3_ACL, CannedAccessControlList.Private.name());
    if (acl.isEmpty()) {
      invalids.put(S3_ACL, "Should be present");
    }
    if (!OUR_OBJECT_KEY_PATTERN.matcher(pathPrefix).matches()) {
      invalids.put(S3_PATH_PREFIX_SETTING, "Should match the regexp [" + OUR_OBJECT_KEY_PATTERN.pattern() + "]");
    }
    final Pair<Long, String> partSizeValueWithError = parseMultipartUploadByteSetting(params.get(S3_MULTIPART_MINIMUM_UPLOAD_PART_SIZE));
    if (partSizeValueWithError.getSecond() != null) {
      invalids.put(S3_MULTIPART_MINIMUM_UPLOAD_PART_SIZE, "Invalid " + partSizeValueWithError.getSecond());
    }
    final Pair<Long, String> thresholdWithError = parseMultipartUploadByteSetting(params.get(S3_MULTIPART_UPLOAD_THRESHOLD));
    if (thresholdWithError.getSecond() != null) {
      invalids.put(S3_MULTIPART_UPLOAD_THRESHOLD, "Invalid " + thresholdWithError.getSecond());
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
    return params.get(REGION_NAME_PARAM);
  }

  public static boolean getCloudFrontEnabled(@NotNull final Map<String, String> params) {
    return Boolean.parseBoolean(params.get(CloudFrontConstants.S3_CLOUDFRONT_ENABLED));
  }

  @Nullable
  public static String getCloudFrontDistribution(@NotNull final Map<String, String> params) {
    return params.get(CloudFrontConstants.S3_CLOUDFRONT_DISTRIBUTION);
  }

  @Nullable
  public static String getCloudFrontPrivateKey(@NotNull final Map<String, String> params) {
    return params.get(CloudFrontConstants.S3_CLOUDFRONT_PRIVATE_KEY);
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
      if (byteSetting <= MULTIPART_UPLOAD_MIN_VALUE_IN_BYTES) {
        return Pair.create(null, "[" + value + "], should be larger than " + MULTIPART_UPLOAD_MIN_VALUE);
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

  public static int getNumberOfThreads(@NotNull final Map<String, String> configuration) {
    return Integer.parseInt(
      configuration.getOrDefault(S3_NUMBER_OF_THREADS, String.valueOf(TeamCityProperties.getInteger(TRANSFER_MANAGER_THREAD_POOL_SIZE, DEFAULT_S3_THREAD_POOL_SIZE))));
  }

  public static int getUrlTtlSeconds(@NotNull final Map<String, String> configuration) {
    return Integer.parseInt(configuration.getOrDefault(S3_URL_LIFETIME_SEC, String.valueOf(TeamCityProperties.getInteger(S3_URL_LIFETIME_SEC, DEFAULT_URL_LIFETIME_SEC))));
  }

  public static boolean isConsistencyCheckEnabled(@NotNull final Map<String, String> configuration) {
    return Boolean.parseBoolean(
      configuration.getOrDefault(S3_ENABLE_CONSISTENCY_CHECK, TeamCityProperties.getProperty(S3_ENABLE_CONSISTENCY_CHECK, String.valueOf(DEFAULT_ENABLE_CONSISTENCY_CHECK))));
  }

  @NotNull
  public static CannedAccessControlList getAcl(@NotNull final Map<String, String> configuration) {
    return CannedAccessControlList.valueOf(configuration.getOrDefault(S3_ACL, CannedAccessControlList.Private.name()));
  }

  private static boolean disablePathStyleAccess(@NotNull final Map<String, String> properties) {
    return Boolean.parseBoolean(properties.get(S3_FORCE_VIRTUAL_HOST_ADDRESSING));
  }

  public static <T, E extends Throwable> T withS3ClientShuttingDownImmediately(@NotNull final Map<String, String> params, @NotNull final WithS3<T, E> withClient) throws E {
    return withS3Client(params, withClient, true);
  }

  public static <T, E extends Throwable> T withS3Client(@NotNull final Map<String, String> params, @NotNull final WithS3<T, E> withClient) throws E {
    return withS3Client(params, withClient, false);
  }

  public static <T, E extends Throwable> T withCloudFrontClient(@NotNull final Map<String, String> params, @NotNull final WithCloudFront<T, E> withClient) throws E {
    return withCloudFrontClient(params, withClient, false);
  }

  private static <T, E extends Throwable> T withCloudFrontClient(@NotNull final Map<String, String> params,
                                                                 @NotNull final WithCloudFront<T, E> withClient,
                                                                 boolean shutdownImmediately) throws E {
    return AWSCommonParams.withAWSClients(params, clients -> {
      clients.setS3SignerType(V4_SIGNER_TYPE);
      clients.setDisablePathStyleAccess(disablePathStyleAccess(params));
      patchAWSClientsSsl(clients, params);
      final AmazonCloudFront client = clients.createCloudFrontClient();
      try {
        return withClient.run(client);
      } finally {
        if (shutdownImmediately) {
          jetbrains.buildServer.util.amazon.S3Util.shutdownClient(client);
        }
      }
    });
  }

  private static <T, E extends Throwable> T withS3Client(@NotNull final Map<String, String> params,
                                                         @NotNull final WithS3<T, E> withClient,
                                                         boolean shutdownImmediately) throws E {
    return AWSCommonParams.withAWSClients(params, clients -> {
      clients.setS3SignerType(V4_SIGNER_TYPE);
      clients.setDisablePathStyleAccess(disablePathStyleAccess(params));
      patchAWSClientsSsl(clients, params);
      final AmazonS3 s3Client = clients.createS3Client();
      try {
        return withClient.run(s3Client);
      } finally {
        if (shutdownImmediately) {
          jetbrains.buildServer.util.amazon.S3Util.shutdownClient(s3Client);
        }
      }
    });
  }

  public static <T extends Transfer> Collection<T> withTransferManagerCorrectingRegion(@NotNull final Map<String, String> s3Settings,
                                                                                       @NotNull final WithTransferManager<T> withTransferManager,
                                                                                       @NotNull final S3AdvancedConfiguration advancedConfiguration)
    throws Throwable {
    try {
      return withTransferManager(s3Settings, withTransferManager, advancedConfiguration);
    } catch (RuntimeException e) {
      final String correctRegion = extractCorrectedRegion(e);
      if (correctRegion != null) {
        LOGGER.debug("Running operation with corrected S3 region [" + correctRegion + "]", e);
        s3Settings.put(REGION_NAME_PARAM, correctRegion);
        return withTransferManager(s3Settings, withTransferManager, advancedConfiguration);
      } else {
        throw e;
      }
    }
  }

  private static <T extends Transfer> Collection<T> withTransferManager(@NotNull final Map<String, String> s3Settings,
                                                                        @NotNull final WithTransferManager<T> withTransferManager,
                                                                        @Nullable final S3AdvancedConfiguration advancedConfiguration)
    throws Throwable {
    final S3AdvancedConfiguration configuration = advancedConfiguration != null ? advancedConfiguration : S3AdvancedConfiguration.defaultConfiguration();
    return AWSCommonParams.withAWSClients(s3Settings, clients -> {
      patchAWSClientsSsl(clients, s3Settings);
      return jetbrains.buildServer.util.amazon.S3Util.withTransferManager(clients.createS3Client(), withTransferManager, configuration);
    });
  }

  private static void patchAWSClientsSsl(@NotNull final AWSClients clients, @NotNull final Map<String, String> params) {
    final ConnectionSocketFactory socketFactory = OUR_SOCKET_FACTORY.socketFactory(params.get(SSL_CERT_DIRECTORY_PARAM));
    if (socketFactory != null) {
      clients.getClientConfiguration().getApacheHttpClientConfig().withSslSocketFactory(socketFactory);
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

  public static <T> T withClientCorrectingRegion(@NotNull final AmazonS3 s3Client,
                                                 @NotNull final Map<String, String> settings,
                                                 @NotNull final WithS3<T, AmazonS3Exception> withCorrectedClient) {
    try {
      return withCorrectedClient.run(s3Client);
    } catch (AmazonS3Exception awsException) {
      final String correctRegion = extractCorrectedRegion(awsException);
      if (correctRegion != null) {
        LOGGER.debug("Running operation with corrected S3 region [" + correctRegion + "]", awsException);
        final HashMap<String, String> correctedSettings = new HashMap<>(settings);
        correctedSettings.put(REGION_NAME_PARAM, correctRegion);
        return withS3ClientShuttingDownImmediately(correctedSettings, withCorrectedClient);
      } else {
        throw awsException;
      }
    }
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

  public interface WithS3<T, E extends Throwable> {
    @Nullable
    T run(@NotNull AmazonS3 client) throws E;
  }

  public interface WithCloudFront<T, E extends Throwable> {
    @Nullable
    T run(@NotNull AmazonCloudFront client) throws E;
  }

}
