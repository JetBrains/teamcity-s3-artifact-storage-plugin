package jetbrains.buildServer.filestorage.cloudfront;

import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.artifacts.s3.S3Util;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class CloudFrontSettingsImpl implements CloudFrontSettings {
  @NotNull
  private final Map<String, String> mySettings;
  @NotNull
  private final RequestMetadata myRequestMetadata;

  CloudFrontSettingsImpl(@NotNull final Map<String, String> params, @NotNull RequestMetadata requestMetadata) {
    mySettings = params;
    myRequestMetadata = requestMetadata;
  }

  @NotNull
  public Map<String, String> toRawSettings() {
    return new HashMap<>(mySettings);
  }

  @Override
  public boolean getCloudFrontEnabled() {
    return S3Util.getCloudFrontEnabled(mySettings);
  }

  @Nullable
  @Override
  public String getCloudFrontDistribution() {
    return S3Util.getCloudFrontDistribution(mySettings);
  }

  @Nullable
  @Override
  public String getCloudFrontPublicKeyId() {
    return S3Util.getCloudFrontPublicKeyId(mySettings);
  }

  @NotNull
  @Override
  public String getCloudFrontPrivateKey() {
    return S3Util.getCloudFrontPrivateKey(mySettings);
  }

  @NotNull
  @Override
  public String getBucketRegion() {
    return S3Util.getBucketRegion(mySettings);
  }

  @Nullable
  @Override
  public String getRequestRegion() {
    return myRequestMetadata.getRegion();
  }

  @Nullable
  @Override
  public String getRequestUserAgent() {
    return myRequestMetadata.getUserAgent();
  }

  @NotNull
  @Override
  public String getBucketName() {
    return S3Util.getBucketName(mySettings);
  }

  @Override
  public int getUrlTtlSeconds() {
    return S3Util.getUrlTtlSeconds(mySettings);
  }
}
