

package jetbrains.buildServer.artifacts.s3;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.util.SdkHttpUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import jetbrains.buildServer.artifacts.s3.amazonClient.AmazonS3Provider;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.TimeService;
import jetbrains.buildServer.util.amazon.AWSException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_URL_LIFETIME_SEC;

/**
 * Created by Evgeniy Koshkin (evgeniy.koshkin@jetbrains.com) on 19.07.17.
 */
public class S3PresignedUrlProviderImpl extends PresignedUrlProvider implements S3PresignedUrlProvider {
  @NotNull
  private static final Logger LOG = Logger.getInstance(S3PresignedUrlProviderImpl.class.getName());
  @NotNull
  private static final String TEAMCITY_S3_OVERRIDE_CONTENT_DISPOSITION = "teamcity.s3.override.content.disposition.enabled";
  @NotNull
  private final TimeService myTimeService;

  public S3PresignedUrlProviderImpl(@NotNull final TimeService timeService, @NotNull final AmazonS3Provider amazonS3Provider) {
    super(amazonS3Provider);
    myTimeService = timeService;
  }

  @NotNull
  @Override
  public PresignedUrlWithTtl generateDownloadUrl(@NotNull final HttpMethod httpMethod, @NotNull final String objectKey, @NotNull final S3Settings settings) throws IOException {
    int urlTtlSeconds = getUrlTtlSeconds(objectKey, settings, true);
    return new PresignedUrlWithTtl(generateUrl(httpMethod, objectKey, null, null, null, settings, urlTtlSeconds), urlTtlSeconds);
  }

  @NotNull
  @Override
  public String generateUploadUrl(@NotNull final String objectKey, @Nullable final String digest, @NotNull final S3Settings settings) throws IOException {
    return generateUrl(HttpMethod.PUT, objectKey, digest, null, null, settings, getUrlTtlSeconds(objectKey, settings, false));
  }

  @NotNull
  @Override
  public String generateUploadUrlForPart(@NotNull final String objectKey,
                                         @Nullable String digest,
                                         final int nPart,
                                         @NotNull final String uploadId,
                                         @NotNull final S3Settings settings) throws IOException {
    return generateUrl(HttpMethod.PUT, objectKey, digest, nPart, uploadId, settings, getUrlTtlSeconds(objectKey, settings, false));
  }

  @NotNull
  private String generateUrl(@NotNull final HttpMethod httpMethod,
                             @NotNull final String objectKey,
                             @Nullable String digest,
                             @Nullable final Integer nPart,
                             @Nullable final String uploadId,
                             @NotNull final S3Settings settings,
                             int urlTtlSeconds) throws IOException {
    try {
      final GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(settings.getBucketName(), objectKey, httpMethod)
        .withExpiration(new Date(myTimeService.now() + urlTtlSeconds * 1000L));
      if (nPart != null) {
        request.addRequestParameter("partNumber", String.valueOf(nPart));
      }
      if (uploadId != null) {
        request.addRequestParameter("uploadId", uploadId);
      }
      if (S3Util.isConsistencyCheckEnabled(settings.toRawSettings()) && digest != null) {
        request.setContentMd5(digest);
      }

      ResponseHeaderOverrides headerOverrides = new ResponseHeaderOverrides();

      if (HttpMethod.GET.equals(httpMethod)) {
        String contentType = getObjectMetadata(objectKey, settings)
          .map(ObjectMetadata::getContentType)
          .map(it -> {
            if (it.indexOf("charset") < 0) {
              return it + ";" + S3Util.DEFAULT_CHARSET;
            }

            return it;
          })
          .orElse(S3Util.DEFAULT_CONTENT_TYPE);

        headerOverrides.withContentType(contentType);
      }

      if (TeamCityProperties.getBooleanOrTrue(TEAMCITY_S3_OVERRIDE_CONTENT_DISPOSITION)) {
        final List<String> split = StringUtil.split(objectKey, "/");
        if (!split.isEmpty()) {
          //Unfortunately S3 expects everything to be ISO-8859-1 compliant. We have to encode filename to allow any non-ISO-8859-1 characters
          String filename = SdkHttpUtils.urlEncode(split.get(split.size() - 1), false);
          headerOverrides.withContentDisposition("inline; filename=\"" + filename + "\"");
        }
      }
      //This header ensures that bucket owner always has access to uploaded objects
      if ((httpMethod == HttpMethod.PUT || httpMethod == HttpMethod.POST) && nPart == null) {
        request.putCustomRequestHeader("x-amz-acl", settings.getAcl().toString());
      }

      request.withResponseHeaders(headerOverrides);
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
  public String startMultipartUpload(@NotNull final String objectKey, @Nullable String contentType, @NotNull final S3Settings settings) throws Exception {
    return callS3(client -> {
      final InitiateMultipartUploadRequest initiateMultipartUploadRequest = new InitiateMultipartUploadRequest(
        settings.getBucketName(),
        objectKey
      );

      if (contentType != null) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(contentType);
        initiateMultipartUploadRequest.withObjectMetadata(metadata);
      }

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

    @Override
    public int getUrlExtendedTtlSeconds() {
      return S3Util.getUrlExtendedTtlSeconds(mySettings);
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
