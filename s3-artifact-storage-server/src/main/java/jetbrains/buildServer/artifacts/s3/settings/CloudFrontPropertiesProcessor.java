package jetbrains.buildServer.artifacts.s3.settings;

import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.artifacts.s3.ValidateCloudFrontKeys;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.util.StringUtil;

import static jetbrains.buildServer.artifacts.s3.cloudfront.CloudFrontConstants.*;

public class CloudFrontPropertiesProcessor implements PropertiesProcessor {

  @Override
  public Collection<InvalidProperty> process(Map<String, String> params) {
    final ArrayList<InvalidProperty> invalids = new ArrayList<>();

    if (StringUtil.isEmptyOrSpaces(S3Util.getCloudFrontUploadDistribution(params))) {
      invalids.add(new InvalidProperty(S3_CLOUDFRONT_UPLOAD_DISTRIBUTION, "CloudFront distribution for upload should not be empty"));
    }

    if (StringUtil.isEmptyOrSpaces(S3Util.getCloudFrontDownloadDistribution(params))) {
      invalids.add(new InvalidProperty(S3_CLOUDFRONT_DOWNLOAD_DISTRIBUTION, "CloudFront distribution for download should not be empty"));
    }

    checkPrivateKey(params, invalids);

    String cloudFrontPublicKeyId = S3Util.getCloudFrontPublicKeyId(params);
    if (cloudFrontPublicKeyId == null) {
      invalids.add(new InvalidProperty(S3_CLOUDFRONT_PUBLIC_KEY_ID, "CloudFront public key should not be empty"));
    }
    return invalids;
  }

  private void checkPrivateKey(Map<String, String> params, ArrayList<InvalidProperty> invalids) {
    try {
      PrivateKey privateKey = ValidateCloudFrontKeys.getPrivateKey(params);
      if (privateKey == null) {
        invalids.add(new InvalidProperty(S3_CLOUDFRONT_PRIVATE_KEY, "CloudFront private key should not be empty"));
      }
    } catch (Exception e) {
      invalids.add(new InvalidProperty(S3_CLOUDFRONT_PRIVATE_KEY, "Error while processing private key in CloudFront settings: " + e.getMessage()));
    }
  }
}
