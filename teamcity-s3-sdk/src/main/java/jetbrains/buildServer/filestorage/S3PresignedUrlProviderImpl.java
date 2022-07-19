/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

package jetbrains.buildServer.filestorage;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.util.SdkHttpUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.TimeService;
import jetbrains.buildServer.util.amazon.AWSException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_URL_LIFETIME_SEC;

/**
 * Created by Evgeniy Koshkin (evgeniy.koshkin@jetbrains.com) on 19.07.17.
 */
public class S3PresignedUrlProviderImpl implements S3PresignedUrlProvider {
  @NotNull
  private static final Logger LOG = Logger.getInstance(S3PresignedUrlProviderImpl.class.getName());
  @NotNull
  private static final String TEAMCITY_S3_OVERRIDE_CONTENT_DISPOSITION = "teamcity.s3.override.content.disposition.enabled";
  @NotNull
  private final TimeService myTimeService;

  public S3PresignedUrlProviderImpl(@NotNull final TimeService timeService) {
    myTimeService = timeService;
  }

  @NotNull
  @Override
  public String generateDownloadUrl(@NotNull final HttpMethod httpMethod, @NotNull final String objectKey, @NotNull final S3Settings settings) throws IOException {
    return generateUrl(httpMethod, objectKey, null, null, null, settings);
  }

  @NotNull
  @Override
  public String generateUploadUrl(@NotNull final String objectKey, @Nullable final String digest, @NotNull final S3Settings settings) throws IOException {
    return generateUrl(HttpMethod.PUT, objectKey, digest, null, null, settings);
  }

  @NotNull
  @Override
  public String generateUploadUrlForPart(@NotNull final String objectKey,
                                         @Nullable String digest,
                                         final int nPart,
                                         @NotNull final String uploadId,
                                         @NotNull final S3Settings settings) throws IOException {
    return generateUrl(HttpMethod.PUT, objectKey, digest, nPart, uploadId, settings);
  }

  @NotNull
  private String generateUrl(@NotNull final HttpMethod httpMethod,
                             @NotNull final String objectKey,
                             @Nullable String digest,
                             @Nullable final Integer nPart,
                             @Nullable final String uploadId,
                             @NotNull final S3Settings settings) throws IOException {
    try {
      final GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(settings.getBucketName(), objectKey, httpMethod)
        .withExpiration(new Date(myTimeService.now() + settings.getUrlTtlSeconds() * 1000L));
      if (nPart != null) {
        request.addRequestParameter("partNumber", String.valueOf(nPart));
      }
      if (uploadId != null) {
        request.addRequestParameter("uploadId", uploadId);
      }
      if (S3Util.isConsistencyCheckEnabled(settings.getProjectSettings()) && digest != null) {
        request.setContentMd5(digest);
      }

      if (TeamCityProperties.getBooleanOrTrue(TEAMCITY_S3_OVERRIDE_CONTENT_DISPOSITION)) {
        final List<String> split = StringUtil.split(objectKey, "/");
        if (!split.isEmpty()) {
          //Unfortunately S3 expects everything to be ISO-8859-1 compliant. We have to encode filename to allow any non-ISO-8859-1 characters
          String filename = SdkHttpUtils.urlEncode(split.get(split.size() - 1), false);
          request.withResponseHeaders(new ResponseHeaderOverrides()
                                        .withContentDisposition("inline; filename=\"" + filename + "\""));
        }
      }
      //This header ensures that bucket owner always has access to uploaded objects
      if ((httpMethod == HttpMethod.PUT || httpMethod == HttpMethod.POST) && nPart == null) {
        request.putCustomRequestHeader("x-amz-acl", settings.getAcl().toString());
      }
      return callS3(client -> client.generatePresignedUrl(request).toString(), settings);
    } catch (Exception e) {
      final Throwable cause = e.getCause();
      final AWSException awsException = cause != null ? new AWSException(cause) : new AWSException(e);
      final String details = awsException.getDetails();
      if (StringUtil.isNotEmpty(details)) {
        final String message = awsException.getMessage() + details;
        LOG.warnAndDebugDetails(message, cause);
      }
      throw new IOException(String.format("Failed to create pre-signed URL to %s artifact '%s' in bucket '%s': %s",
                                          httpMethod.name().toLowerCase(), objectKey, settings.getBucketName(), awsException.getMessage()), awsException);
    }
  }

  @NotNull
  @Override
  public String startMultipartUpload(@NotNull final String objectKey, @NotNull final S3Settings settings) throws Exception {
    return callS3(client -> {
      final InitiateMultipartUploadRequest initiateMultipartUploadRequest = new InitiateMultipartUploadRequest(settings.getBucketName(), objectKey);
      initiateMultipartUploadRequest.setCannedACL(settings.getAcl());
      final InitiateMultipartUploadResult initiateMultipartUploadResult = client.initiateMultipartUpload(initiateMultipartUploadRequest);
      return initiateMultipartUploadResult.getUploadId();
    }, settings);
  }

  @Override
  public void finishMultipartUpload(@NotNull final String uploadId,
                                    @NotNull final String objectKey,
                                    @NotNull final S3Settings settings,
                                    @Nullable final String[] etags,
                                    final boolean isSuccessful) throws IOException {
    callS3(client -> {
      if (isSuccessful) {
        if (etags == null || etags.length == 0) {
          throw new IllegalArgumentException("Cannot complete multipart request without etags");
        }
        final List<PartETag> partETags = new ArrayList<>();
        for (int i = 0; i < etags.length; i++) {
          partETags.add(new PartETag(i + 1, etags[i]));
        }
        client.completeMultipartUpload(new CompleteMultipartUploadRequest(settings.getBucketName(), objectKey, uploadId, partETags));
      } else {
        client.abortMultipartUpload(new AbortMultipartUploadRequest(settings.getBucketName(), objectKey, uploadId));
      }
      return null;
    }, settings);
  }

  private <T> T callS3(@NotNull final Function<AmazonS3, T> callable, @NotNull final S3Settings settings) throws IOException {
    return S3Util.withS3ClientShuttingDownImmediately(settings.toRawSettings(), client -> {
      try {
        return callable.apply(client);
      } catch (final Throwable t) {
        throw new IOException(t);
      }
    });
  }

  @NotNull
  public S3Settings settings(@NotNull final Map<String, String> rawSettings, @NotNull Map<String, String> projectSettings) {
    if (S3Util.getBucketName(rawSettings) == null) {
      throw new IllegalArgumentException("Settings don't contain bucket name");
    }
    return new S3SettingsImpl(rawSettings, projectSettings);
  }

  private static class S3SettingsImpl implements S3Settings {
    @NotNull
    private final Map<String, String> mySettings;
    @NotNull
    private final Map<String, String> myProjectSettings;

    private S3SettingsImpl(@NotNull final Map<String, String> params, @NotNull Map<String, String> projectSettings) {
      mySettings = new HashMap<>(params);
      myProjectSettings = projectSettings;
    }

    @NotNull
    @Override
    public String getBucketName() {
      return Objects.requireNonNull(S3Util.getBucketName(mySettings));
    }

    @Override
    public int getUrlTtlSeconds() {
      return S3Util.getUrlTtlSeconds(mySettings);
    }

    @NotNull
    @Override
    public CannedAccessControlList getAcl() {
      return S3Util.getAcl(mySettings, myProjectSettings);
    }

    @NotNull
    public Map<String, String> toRawSettings() {
      return new HashMap<>(mySettings);
    }

    @Override
    @NotNull
    public Map<String, String> getProjectSettings() {
      return new HashMap<>(myProjectSettings);
    }

    @Override
    public void setTtl(long ttl) {
      mySettings.put(S3_URL_LIFETIME_SEC, String.valueOf(ttl));
    }
  }
}
