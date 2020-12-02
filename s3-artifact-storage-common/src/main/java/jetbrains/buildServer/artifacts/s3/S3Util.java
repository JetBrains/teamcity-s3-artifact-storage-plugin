/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.transfer.Transfer;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URLConnection;
import java.security.KeyStore;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import javax.net.ssl.SSLContext;
import jetbrains.buildServer.artifacts.ArtifactListData;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.ExceptionUtil;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.amazon.AWSClients;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import jetbrains.buildServer.util.amazon.S3Util.S3AdvancedConfiguration;
import jetbrains.buildServer.util.amazon.S3Util.WithTransferManager;
import jetbrains.buildServer.util.ssl.SSLContextUtil;
import jetbrains.buildServer.util.ssl.TrustStoreIO;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.artifacts.s3.S3Constants.*;
import static jetbrains.buildServer.util.amazon.AWSCommonParams.REGION_NAME_PARAM;
import static jetbrains.buildServer.util.amazon.AWSCommonParams.SSL_CERT_DIRECTORY_PARAM;

/**
 * Created by Nikita.Skvortsov
 * date: 02.08.2016.
 */
public class S3Util {
  @NotNull
  private static final Pattern OUR_OBJECT_KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9!/\\-_.*'()]+$");
  private static final int OUT_MAX_PREFIX_LENGTH = 127;
  @NotNull
  private static final Logger LOGGER = Logger.getInstance(S3Util.class.getName());
  @NotNull
  private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
  @NotNull
  private static final Method PROBE_CONTENT_TYPE_METHOD = getProbeContentTypeMethod();
  @NotNull
  private static final Method FILE_TO_PATH_METHOD = getFileToPathMethod();
  @NotNull
  private static final String V4_SIGNER_TYPE = "AWSS3V4SignerType";

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
    if (TeamCityProperties.getBooleanOrTrue("teamcity.internal.storage.s3.bucket.prefix.enable") && !StringUtil.isEmptyOrSpaces(pathPrefix)) {
      if (pathPrefix.length() > OUT_MAX_PREFIX_LENGTH) {
        invalids.put(S3_PATH_PREFIX_SETTING, "Should be less than " + OUT_MAX_PREFIX_LENGTH + " characters");
      }
      if (!OUR_OBJECT_KEY_PATTERN.matcher(pathPrefix).matches()) {
        invalids.put(S3_PATH_PREFIX_SETTING, "Should match the regexp [" + OUR_OBJECT_KEY_PATTERN.pattern() + "]");
      }
    }
    return invalids;
  }

  @NotNull
  public static Map<String, String> validateParameters(@NotNull final Map<String, String> params) throws IllegalArgumentException {
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
    try {
      final int nRetries = Integer.parseInt(configurationParameters.get(S3_NUMBER_OF_RETRIES_ON_ERROR));
      return nRetries >= 0 ? nRetries : DEFAULT_S3_NUMBER_OF_RETRIES_ON_ERROR;
    } catch (NumberFormatException e) {
      return DEFAULT_S3_NUMBER_OF_RETRIES_ON_ERROR;
    }
  }

  @Nullable
  public static Long getMultipartUploadThreshold(@NotNull final Map<String, String> configurationParameters) {
    final String stringValue = configurationParameters.get(S3_MULTIPART_UPLOAD_THRESHOLD);
    try {
      final long multipartThreshold = Long.parseLong(stringValue);
      if (multipartThreshold < 1024 * 1024 * 5 + 1) {
        LOGGER.warn(S3_MULTIPART_UPLOAD_THRESHOLD + " [" + multipartThreshold + "] should be larger than 5MB (" + (1024 * 1024 * 5 + 1) + "), the default value will be used");
        return null;
      }
      return multipartThreshold;
    } catch (NumberFormatException e) {
      LOGGER.warn(S3_MULTIPART_UPLOAD_THRESHOLD + " [" + stringValue + "] should be integer");
      return null;
    }
  }

  @Nullable
  public static Long getMinimumUploadPartSize(@NotNull final Map<String, String> configurationParameters) {
    final String stringValue = configurationParameters.get(S3_MULTIPART_MINIMUM_UPLOAD_PART_SIZE);
    try {
      final long multipartChunkSize = Long.parseLong(stringValue);
      if (multipartChunkSize < 1024 * 1024 * 5 + 1) {
        LOGGER.warn(S3_MULTIPART_MINIMUM_UPLOAD_PART_SIZE + " [" + multipartChunkSize + "] is unreasonably small and will slow down the upload");
      }
      return multipartChunkSize >= 0 ? multipartChunkSize : null;
    } catch (NumberFormatException e) {
      LOGGER.warn(S3_MULTIPART_MINIMUM_UPLOAD_PART_SIZE + " [" + stringValue + "] should be integer");
      return null;
    }
  }

  public static int getRetryDelayInMs(@NotNull final Map<String, String> configurationParameters) {
    try {
      final int delay = Integer.parseInt(configurationParameters.get(S3_RETRY_DELAY_MS_ON_ERROR));
      return delay >= 0 ? delay : DEFAULT_S3_RETRY_DELAY_ON_ERROR_MS;
    } catch (NumberFormatException e) {
      return DEFAULT_S3_RETRY_DELAY_ON_ERROR_MS;
    }
  }

  private static boolean useSignatureVersion4(@NotNull final Map<String, String> properties) {
    return Boolean.parseBoolean(properties.get(S3_USE_SIGNATURE_V4));
  }

  private static boolean disablePathStyleAccess(@NotNull final Map<String, String> properties) {
    return Boolean.parseBoolean(properties.get(S3_FORCE_VIRTUAL_HOST_ADDRESSING));
  }

  @Nullable
  private static KeyStore trustStore(@Nullable final String directory) {
    if (directory == null) {
      return null;
    }
    return TrustStoreIO.readTrustStoreFromDirectory(directory);
  }

  @Nullable
  private static ConnectionSocketFactory socketFactory(@NotNull final Map<String, String> params) {
    final String certDirectory = params.get(SSL_CERT_DIRECTORY_PARAM);
    if (certDirectory == null) {
      return null;
    }
    final KeyStore trustStore = trustStore(certDirectory);
    if (trustStore == null) {
      return null;
    }
    final SSLContext sslContext = SSLContextUtil.createUserSSLContext(trustStore);
    if (sslContext == null) {
      return null;
    }
    return new SSLConnectionSocketFactory(sslContext);
  }

  public static <T, E extends Throwable> T withS3ClientShuttingDownImmediately(@NotNull final Map<String, String> params, @NotNull final WithS3<T, E> withClient) throws E {
    return withS3Client(params, withClient, true);
  }

  public static <T, E extends Throwable> T withS3Client(@NotNull final Map<String, String> params, @NotNull final WithS3<T, E> withClient) throws E {
    return withS3Client(params, withClient, false);
  }

  private static <T, E extends Throwable> T withS3Client(@NotNull final Map<String, String> params,
                                                         @NotNull final WithS3<T, E> withClient,
                                                         boolean shutdownImmediately) throws E {
    return AWSCommonParams.withAWSClients(params, clients -> {
      if (useSignatureVersion4(params)) {
        clients.setS3SignerType(V4_SIGNER_TYPE);
      }
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
    return AWSCommonParams.withAWSClients(s3Settings, clients -> {
      patchAWSClientsSsl(clients, s3Settings);
      return jetbrains.buildServer.util.amazon.S3Util.withTransferManager(clients.createS3Client(), withTransferManager, advancedConfiguration);
    });
  }

  private static void patchAWSClientsSsl(@NotNull final AWSClients clients, @NotNull final Map<String, String> params) {
    final ConnectionSocketFactory socketFactory = socketFactory(params);
    if (socketFactory != null) {
      clients.getClientConfiguration().getApacheHttpClientConfig().withSslSocketFactory(socketFactory);
    }
  }

  public static String getContentType(final File file) {
    String contentType = URLConnection.guessContentTypeFromName(file.getName());
    if (StringUtil.isNotEmpty(contentType)) {
      return contentType;
    }
    if (PROBE_CONTENT_TYPE_METHOD != null && FILE_TO_PATH_METHOD != null) {
      try {
        final Object result = PROBE_CONTENT_TYPE_METHOD.invoke(null, FILE_TO_PATH_METHOD.invoke(file));
        if (result instanceof String) {
          contentType = (String)result;
        }
      } catch (Exception ignored) {
      }
    }
    return StringUtil.notEmpty(contentType, DEFAULT_CONTENT_TYPE);
  }

  public static String normalizeArtifactPath(final String path, final File file) {
    if (StringUtil.isEmpty(path)) {
      return file.getName();
    } else {
      return FileUtil.normalizeRelativePath(String.format("%s/%s", path, file.getName()));
    }
  }

  private static Method getProbeContentTypeMethod() {
    try {
      final Class<?> filesClass = Class.forName("java.nio.file.Files");
      final Class<?> pathClass = Class.forName("java.nio.file.Path");
      if (filesClass != null && pathClass != null) {
        return filesClass.getMethod("probeContentType", pathClass);
      }
    } catch (Exception ignored) {
    }
    return null;
  }

  private static Method getFileToPathMethod() {
    try {
      return File.class.getMethod("toPath");
    } catch (Exception ignored) {
    }
    return null;
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

  public static class InvalidSettingsException extends RuntimeException {
    private final Map<String, String> myInvalids;

    public InvalidSettingsException(@NotNull final Map<String, String> invalids) {
      myInvalids = new HashMap<>(invalids);
    }

    @Override
    public String getMessage() {
      return StringUtil.join("\n", myInvalids.values());
    }

    @NotNull
    public Map<String, String> getInvalids() {
      return myInvalids;
    }
  }
}
