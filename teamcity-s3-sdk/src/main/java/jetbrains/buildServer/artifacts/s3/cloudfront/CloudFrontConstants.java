package jetbrains.buildServer.artifacts.s3.cloudfront;

public class CloudFrontConstants {
  public static final String S3_ENABLE_CLOUDFRONT_INTEGRATION = "teamcity.s3.use.cloudfront.enabled";

  public static final String S3_CLOUDFRONT_ENABLED = "storage.s3.cloudfront.enabled";
  public static final String S3_CLOUDFRONT_DISTRIBUTION = "storage.s3.cloudfront.distribution";
  public static final String S3_CLOUDFRONT_DOMAIN = "storage.s3.cloudfront.domain";
  public static final String S3_CLOUDFRONT_PUBLIC_KEY_ID = "storage.s3.cloudfront.publicKeyId";
  public static final String S3_CLOUDFRONT_SETTINGS_GROUP = "storage.s3.cloudfront.settings";
  public static final String S3_CLOUDFRONT_PRIVATE_SSH_KEY = "teamcitySshKey";

  public static final String S3_CLOUDFRONT_DEFAULT_OAI_COMMENT = "teamcity.cloudfront.access";
  public static final String S3_CLOUDFRONT_CACHE_POLICY = "Managed-CachingOptimized";
  public static final String S3_CLOUDFRONT_OAI_SUBSTRING = "CloudFront Origin Access Identity";
  public static final String S3_CLOUDFRONT_OAI_ID_TEMPLATE = "origin-access-identity/cloudfront/%s";
  public static final String S3_CLOUDFRONT_PRINCIPAL_TEMPLATE = "arn:aws:iam::cloudfront:user/CloudFront Origin Access Identity %s";
}
