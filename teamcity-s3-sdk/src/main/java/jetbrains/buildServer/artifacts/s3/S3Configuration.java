package jetbrains.buildServer.artifacts.s3;

import java.util.Map;
import jetbrains.buildServer.util.amazon.S3Util.S3AdvancedConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class S3Configuration {
  @NotNull
  private final S3AdvancedConfiguration myS3AdvancedConfiguration;
  @NotNull
  private final Map<String, String> mySettingsMap;
  @Nullable
  private String myPathPrefix;

  public S3Configuration(@NotNull final S3AdvancedConfiguration s3AdvancedConfiguration, @NotNull final Map<String, String> settingsMap) {
    this.myS3AdvancedConfiguration = s3AdvancedConfiguration;
    this.mySettingsMap = settingsMap;
  }

  @NotNull
  public String getBucketName() {
    return S3Util.getBucketName(mySettingsMap);
  }

  @NotNull
  public Map<String, String> getSettingsMap() {
    return mySettingsMap;
  }

  public boolean isUsePresignedUrls() {
    return S3Util.usePreSignedUrls(mySettingsMap);
  }

  @NotNull
  public String getPathPrefix() {
    return this.myPathPrefix != null ? this.myPathPrefix : "";
  }

  public void setPathPrefix(@NotNull String pathPrefix) {
    this.myPathPrefix = pathPrefix;
  }

  @NotNull
  public S3AdvancedConfiguration getAdvancedConfiguration() {
    return myS3AdvancedConfiguration;
  }

  public void validate() {
    if (!isUsePresignedUrls()) {
      S3Util.validateParameters(mySettingsMap);
    }
  }
}
