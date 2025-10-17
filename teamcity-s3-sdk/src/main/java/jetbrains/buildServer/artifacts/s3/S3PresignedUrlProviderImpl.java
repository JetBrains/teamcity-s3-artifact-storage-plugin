

package jetbrains.buildServer.artifacts.s3;

import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;
import software.amazon.awssdk.utils.http.SdkHttpUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import jetbrains.buildServer.artifacts.s3.amazonClient.AmazonS3Provider;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.amazon.AWSException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_URL_LIFETIME_SEC;

/**
 * Created by Evgeniy Koshkin (evgeniy.koshkin@jetbrains.com) on 19.07.17.
 */
public class S3PresignedUrlProviderImpl extends PresignedUrlProvider implements S3PresignedUrlProvider {
  @NotNull
  private static final Logger LOG = Logger.getInstance(S3PresignedUrlProviderImpl.class.getName());
  @NotNull
  private static final String TEAMCITY_S3_OVERRIDE_CONTENT_DISPOSITION = "teamcity.s3.override.content.disposition.enabled";

  public S3PresignedUrlProviderImpl(@NotNull final AmazonS3Provider amazonS3Provider) {
    super(amazonS3Provider);
  }

  @NotNull
  @Override
  public PresignedUrlWithTtl generateDownloadUrl(@NotNull final SdkHttpMethod httpMethod, @NotNull final String objectKey, @NotNull final S3Settings settings) throws IOException {
    int urlTtlSeconds = getUrlTtlSeconds(objectKey, settings, true);
    GetObjectRequest.Builder objectRequestBuilder = GetObjectRequest.builder()
                                                     .bucket(settings.getBucketName())
                                                     .key(objectKey)
                                                     .responseContentType(getContentType(objectKey, settings));
    getContentDisposition(objectKey).ifPresent(objectRequestBuilder::responseContentDisposition);
    GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                                                                    .signatureDuration(Duration.ofSeconds(urlTtlSeconds))
                                                                    .getObjectRequest(objectRequestBuilder.build())
                                                                    .build();
    return new PresignedUrlWithTtl(generateUrl(httpMethod, objectKey, p -> p.presignGetObject(presignRequest).url().toExternalForm(), settings), urlTtlSeconds);
  }

  @NotNull
  @Override
  public String generateUploadUrl(@NotNull final String objectKey, @Nullable final String digest, @NotNull final S3Settings settings) throws IOException {
    int urlTtlSeconds = getUrlTtlSeconds(objectKey, settings, false);
    PutObjectRequest.Builder putObjectRequestBuilder = PutObjectRequest.builder()
                                                        .bucket(settings.getBucketName())
                                                        .key(objectKey)
                                                        .acl(settings.getAcl().toString());
    getContentMd5(settings, digest).ifPresent(putObjectRequestBuilder::contentMD5);
    PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                                                                    .signatureDuration(Duration.ofSeconds(urlTtlSeconds))
                                                                    .putObjectRequest(putObjectRequestBuilder.build())
                                                                    .build();
    return generateUrl(SdkHttpMethod.PUT, objectKey, p -> p.presignPutObject(presignRequest).url().toExternalForm(), settings);
  }

  @NotNull
  @Override
  public String generateUploadUrlForPart(@NotNull final String objectKey,
                                         @Nullable String digest,
                                         final int nPart,
                                         @NotNull final String uploadId,
                                         @NotNull final S3Settings settings) throws IOException {
    int urlTtlSeconds = getUrlTtlSeconds(objectKey, settings, false);
    UploadPartRequest.Builder uploadPartRequestBuilder = UploadPartRequest.builder()
                                                         .bucket(settings.getBucketName())
                                                         .key(objectKey)
                                                         .partNumber(nPart)
                                                         .uploadId(uploadId);
    getContentMd5(settings, digest).ifPresent(uploadPartRequestBuilder::contentMD5);
    UploadPartPresignRequest presignRequest = UploadPartPresignRequest.builder()
                                                                     .signatureDuration(Duration.ofSeconds(urlTtlSeconds))
                                                                     .uploadPartRequest(uploadPartRequestBuilder.build())
                                                                     .build();
    return generateUrl(SdkHttpMethod.PUT, objectKey, p -> p.presignUploadPart(presignRequest).url().toExternalForm(), settings);
  }

  @NotNull
  private String generateUrl(@NotNull final SdkHttpMethod httpMethod,
                             @NotNull final String objectKey,
                             @NotNull final Function<S3Presigner, String> withS3Presginer,
                             @NotNull final S3Settings settings) throws IOException {
    try {
      return callS3Presign(withS3Presginer, settings);
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
      final CreateMultipartUploadRequest.Builder initiateMultipartUploadRequest = CreateMultipartUploadRequest.builder()
                                                                                              .bucket(settings.getBucketName())
                                                                                              .key(objectKey)
                                                                                              .acl(settings.getAcl());

      if (contentType != null) {
        initiateMultipartUploadRequest.contentType(contentType);
      }

      final CreateMultipartUploadResponse initiateMultipartUploadResult = client.createMultipartUpload(initiateMultipartUploadRequest.build());
      return initiateMultipartUploadResult.uploadId();
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
        final List<CompletedPart> partETags = new ArrayList<>();
        for (int i = 0; i < etags.length; i++) {
          partETags.add(CompletedPart.builder().partNumber(i + 1).eTag(etags[i]).build());
        }
        client.completeMultipartUpload(b -> b
          .bucket(settings.getBucketName())
          .key(objectKey)
          .uploadId(uploadId)
          .multipartUpload(partBuilder -> partBuilder.parts(partETags))
        );
      } else {
        client.abortMultipartUpload(b -> b
          .bucket(settings.getBucketName())
          .key(objectKey)
          .uploadId(uploadId)
        );
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

  private String getContentType(@NotNull final String objectKey, @NotNull final S3Settings settings) {
    return getObjectMetadata(objectKey, settings)
      .map(HeadObjectResponse::contentType)
      .map(it -> {
        if (it.indexOf("charset") < 0) {
          return it + ";" + S3Util.DEFAULT_CHARSET;
        }

        return it;
      })
      .orElse(S3Util.DEFAULT_CONTENT_TYPE);
  }

  private static Optional<String> getContentDisposition(@NotNull final String objectKey) {
    if (TeamCityProperties.getBooleanOrTrue(TEAMCITY_S3_OVERRIDE_CONTENT_DISPOSITION)) {
      final List<String> split = StringUtil.split(objectKey, "/");
      if (!split.isEmpty()) {
        //Unfortunately S3 expects everything to be ISO-8859-1 compliant. We have to encode filename to allow any non-ISO-8859-1 characters
        String filename = SdkHttpUtils.urlEncode(split.get(split.size() - 1));
        return Optional.of("inline; filename=\"" + filename + "\"");
      }
    }
    return Optional.empty();
  }

  private static Optional<String> getContentMd5(@NotNull final S3Settings settings, final String digest) {
    if (S3Util.isConsistencyCheckEnabled(settings.toRawSettings()) && digest != null) {
      return Optional.of(digest);
    }
    return Optional.empty();
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
    public ObjectCannedACL getAcl() {
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
