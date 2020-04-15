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

package jetbrains.buildServer.artifacts.s3.preSignedUrl;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.artifacts.s3.util.ParamUtil;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import jetbrains.buildServer.util.amazon.AWSException;
import org.jetbrains.annotations.NotNull;

/**
 * Created by Evgeniy Koshkin (evgeniy.koshkin@jetbrains.com) on 19.07.17.
 */
public class S3PreSignedUrlProviderImpl implements S3PreSignedUrlProvider {
  private static final Logger LOG = Logger.getInstance(S3PreSignedUrlProviderImpl.class.getName());
  private static final String TEAMCITY_S3_PRESIGNURL_GET_CACHE_ENABLED = "teamcity.s3.presignurl.get.cache.enabled";

  private final ServerPaths myServerPaths;

  public S3PreSignedUrlProviderImpl(@NotNull ServerPaths serverPaths) {
    myServerPaths = serverPaths;
  }

  private final Cache<String, String> myGetLinksCache = CacheBuilder.newBuilder()
    .expireAfterWrite(getUrlLifetimeSec(), TimeUnit.SECONDS)
    .maximumSize(200)
    .build();

  @Override
  public int getUrlLifetimeSec() {
    return TeamCityProperties.getInteger(S3Constants.S3_URL_LIFETIME_SEC, S3Constants.DEFAULT_S3_URL_LIFETIME_SEC);
  }

  @NotNull
  @Override
  public String getPreSignedUrl(@NotNull HttpMethod httpMethod, @NotNull String bucketName, @NotNull String objectKey, @NotNull Map<String, String> params) throws IOException {
    try {
      final Callable<String> resolver = () -> S3Util.withS3Client(ParamUtil.putSslValues(myServerPaths, params), client -> {
        final GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketName, objectKey, httpMethod)
          .withExpiration(new Date(System.currentTimeMillis() + getUrlLifetimeSec() * 1000));
        return client.generatePresignedUrl(request).toString();
      });
      if (httpMethod == HttpMethod.GET) {
        return TeamCityProperties.getBoolean(TEAMCITY_S3_PRESIGNURL_GET_CACHE_ENABLED)
          ? myGetLinksCache.get(getCacheIdentity(params, objectKey, bucketName), resolver)
          : resolver.call();
      } else {
        return resolver.call();
      }
    } catch (Exception e) {
      final Throwable cause = e.getCause();
      final AWSException awsException = cause != null ? new AWSException(cause) : new AWSException(e);
      final String details = awsException.getDetails();
      if (StringUtil.isNotEmpty(details)) {
        final String message = awsException.getMessage() + details;
        LOG.warn(message);
      }
      throw new IOException(String.format(
        "Failed to create pre-signed URL to %s artifact '%s' in bucket '%s': %s",
        httpMethod.name().toLowerCase(), objectKey, bucketName, awsException.getMessage()
      ), awsException);
    }
  }

  @NotNull
  private String getCacheIdentity(@NotNull Map<String, String> params, @NotNull String key, @NotNull String bucket) {
    return String.valueOf(AWSCommonParams.calculateIdentity("", params, bucket, key));
  }
}
