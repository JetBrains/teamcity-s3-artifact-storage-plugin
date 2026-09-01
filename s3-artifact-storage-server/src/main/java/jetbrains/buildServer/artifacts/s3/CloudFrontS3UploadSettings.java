package jetbrains.buildServer.artifacts.s3;

import java.util.Map;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

public final class CloudFrontS3UploadSettings {
  public static final String S3_CLOUDFRONT_UPLOAD_USE_S3 = "storage.s3.cloudfront.upload.s3.enabled";

  private CloudFrontS3UploadSettings() {
  }

  public static boolean useS3ForUpload(@NotNull Map<String, String> params) {
    return StringUtil.isTrue(params.get(S3_CLOUDFRONT_UPLOAD_USE_S3));
  }
}
