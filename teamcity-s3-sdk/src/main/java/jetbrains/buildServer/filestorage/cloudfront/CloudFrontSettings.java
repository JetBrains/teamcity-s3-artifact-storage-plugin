package jetbrains.buildServer.filestorage.cloudfront;

import jetbrains.buildServer.filestorage.S3Settings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface CloudFrontSettings extends S3Settings {
  boolean getCloudFrontEnabled();

  @Nullable
  String getCloudFrontDistribution();

  @Nullable
  String getCloudFrontPublicKeyId();

  @NotNull
  String getCloudFrontPrivateKey();

  @NotNull
  String getBucketRegion();

  @Nullable
  String getRequestRegion();

  @Nullable
  String getRequestUserAgent();
}
