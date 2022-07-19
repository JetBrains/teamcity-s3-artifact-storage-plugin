package jetbrains.buildServer.filestorage.cloudfront;

import com.amazonaws.services.s3.model.CannedAccessControlList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.artifacts.s3.cloudfront.CloudFrontConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class CloudFrontSettingsImpl implements CloudFrontSettings {
  @NotNull
  private final Map<String, String> mySettings;
  @NotNull
  private final Map<String, String> myProjectSettings;
  @NotNull
  private final RequestMetadata myRequestMetadata;

  CloudFrontSettingsImpl(@NotNull final Map<String, String> rawSettings, @NotNull Map<String, String> projectSettings, @NotNull RequestMetadata requestMetadata) {
    mySettings = new HashMap<>(rawSettings);
    myProjectSettings = projectSettings;
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

  @NotNull
  @Override
  public String getCloudFrontDistribution() {
    return mySettings.get(CloudFrontConstants.S3_CLOUDFRONT_DISTRIBUTION);
  }

  @Nullable
  @Override
  public String getCloudFrontUploadDistribution() {
    return S3Util.getCloudFrontUploadDistribution(mySettings);
  }

  @Nullable
  @Override
  public String getCloudFrontDownloadDistribution() {
    return S3Util.getCloudFrontDownloadDistribution(mySettings);
  }

  @NotNull
  @Override
  public String getCloudFrontPublicKeyId() {
    return Objects.requireNonNull(S3Util.getCloudFrontPublicKeyId(mySettings));
  }

  @NotNull
  @Override
  public String getCloudFrontPrivateKey() {
    return Objects.requireNonNull(S3Util.getCloudFrontPrivateKey(mySettings));
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
  public Map<String, String> getProjectSettings() {
    return new HashMap<>(myProjectSettings);
  }

  @Override
  public void setTtl(long ttl) {
    mySettings.put(S3Constants.S3_URL_LIFETIME_SEC, String.valueOf(ttl));
  }
}
