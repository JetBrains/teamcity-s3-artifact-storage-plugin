package jetbrains.buildServer.artifacts.s3.cloudfront;

import com.amazonaws.HttpMethod;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import jetbrains.buildServer.artifacts.s3.S3PresignedUrlProvider;
import jetbrains.buildServer.artifacts.s3.S3Settings;
import jetbrains.buildServer.artifacts.s3.S3Util;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CloudFrontEnabledPresignedUrlProviderImpl implements CloudFrontEnabledPresignedUrlProvider {

  private final S3PresignedUrlProvider myS3Provider;
  private final CloudFrontPresignedUrlProvider myCloudFrontProvider;

  public CloudFrontEnabledPresignedUrlProviderImpl(S3PresignedUrlProvider s3Provider, CloudFrontPresignedUrlProvider cloudFrontProvider) {
    myS3Provider = s3Provider;
    myCloudFrontProvider = cloudFrontProvider;
  }

  @Override
  @NotNull
  public String generateDownloadUrl(@NotNull HttpMethod httpMethod,
                                    @NotNull String objectKey,
                                    @NotNull CloudFrontSettings settings) throws IOException {
    String preSignedUrl = null;

    if (shouldUseCloudFront(settings)) {
      preSignedUrl = myCloudFrontProvider.generateDownloadUrl(objectKey, settings);
    }

    if (preSignedUrl == null) {
      preSignedUrl = myS3Provider.generateDownloadUrl(httpMethod, objectKey, settings);
    }
    return preSignedUrl;
  }

  @Override
  @NotNull
  public String generateUploadUrl(@NotNull String objectKey, @Nullable String digest, @NotNull CloudFrontSettings settings) throws IOException {
    String preSignedUrl = null;

    if (shouldUseCloudFront(settings)) {
      preSignedUrl = myCloudFrontProvider.generateUploadUrl(objectKey, settings);
    }

    if (preSignedUrl == null) {
      preSignedUrl = myS3Provider.generateUploadUrl(objectKey, digest, settings);
    }
    return preSignedUrl;
  }

  @Override
  @NotNull
  public String generateUploadUrlForPart(@NotNull String objectKey,
                                         @Nullable String digest,
                                         int nPart,
                                         @NotNull String uploadId,
                                         @NotNull CloudFrontSettings settings) throws IOException {
    String preSignedUrl = null;

    if (shouldUseCloudFront(settings)) {
      preSignedUrl = myCloudFrontProvider.generateUploadUrlForPart(objectKey, nPart, uploadId, settings);
    }

    if (preSignedUrl == null) {
      preSignedUrl = myS3Provider.generateUploadUrlForPart(objectKey, digest, nPart, uploadId, settings);
    }
    return preSignedUrl;
  }

  @Override
  public void finishMultipartUpload(@NotNull String uploadId,
                                    @NotNull String objectKey,
                                    @NotNull CloudFrontSettings settings,
                                    @Nullable String[] etags,
                                    boolean isSuccessful) throws IOException {
    myS3Provider.finishMultipartUpload(uploadId, objectKey, settings, etags, isSuccessful);
  }

  @Override
  @NotNull
  public String startMultipartUpload(@NotNull String objectKey, @NotNull CloudFrontSettings settings) throws Exception {
    return myS3Provider.startMultipartUpload(objectKey, settings);
  }

  @Override
  @NotNull
  public CloudFrontSettings settings(@NotNull Map<String, String> rawSettings, @NotNull Map<String, String> projectSettings, @NotNull RequestMetadata metadata) {
    S3Settings s3Settings = myS3Provider.settings(rawSettings, projectSettings);

    Map<String, String> rawS3Settings = s3Settings.toRawSettings();
    if (S3Util.getBucketName(rawS3Settings) == null) {
      throw new IllegalArgumentException("Settings don't contain bucket name");
    }

    return new CloudFrontSettingsImpl(rawS3Settings, projectSettings, metadata);
  }

  private boolean shouldUseCloudFront(CloudFrontSettings settings) {
    if (CloudFrontConstants.isEnabled() && settings.getCloudFrontEnabled()) {
      String requestRegion = settings.getRequestRegion();
      String bucketRegion = settings.getBucketRegion();
      String userAgent = settings.getRequestUserAgent();

      boolean notAnAgentRequest = userAgent == null || !userAgent.contains("Agent");
      boolean differentRegions = !Objects.equals(bucketRegion, requestRegion);

      return notAnAgentRequest || differentRegions;
    }

    return false;
  }
}
