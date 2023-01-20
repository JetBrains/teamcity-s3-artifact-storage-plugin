package jetbrains.buildServer.artifacts.s3.cloudfront;

import jetbrains.buildServer.artifacts.s3.S3Settings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface CloudFrontSettings extends S3Settings {
  boolean getCloudFrontEnabled();

  @Deprecated
  @NotNull
  String getCloudFrontDistribution();

  @Nullable
  String getCloudFrontUploadDistribution();

  @Nullable
  String getCloudFrontDownloadDistribution();

  @NotNull
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
