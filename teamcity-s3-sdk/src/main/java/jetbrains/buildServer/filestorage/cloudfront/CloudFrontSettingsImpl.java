package jetbrains.buildServer.filestorage.cloudfront;

import com.amazonaws.services.s3.model.CannedAccessControlList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import jetbrains.buildServer.artifacts.s3.S3Util;
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
    mySettings = rawSettings;
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
    return Objects.requireNonNull(S3Util.getBucketName(mySettings));
  }

  @Override
  public int getUrlTtlSeconds() {
    return S3Util.getUrlTtlSeconds(mySettings);
  }

  @NotNull
  @Override
  public CannedAccessControlList getAcl() {
    return S3Util.getAcl(mySettings);
  }

  @NotNull
  public Map<String, String> getProjectSettings() {
    return new HashMap<>(myProjectSettings);
  }
}
