

package jetbrains.buildServer.artifacts.s3.web;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.cloudfront.CloudFrontConstants;
import jetbrains.buildServer.clouds.amazon.connector.utils.parameters.AwsCloudConnectorConstants;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;

public class S3ParametersProvider {
  public String getBucketName() {
    return S3Constants.S3_BUCKET_NAME;
  }

  public String getBucketNameWasProvidedAsString() {
    return S3Constants.S3_BUCKET_NAME_WAS_PROVIDED_AS_STRING;
  }

  public String getUsePresignUrlsForUpload() {
    return S3Constants.S3_USE_PRE_SIGNED_URL_FOR_UPLOAD;
  }

  public String getPluginPath() {
    return S3Constants.S3_PLUGIN_PATH;
  }

  public String getContainersPath() {
    return String.format("%s%s.html", S3Constants.S3_PLUGIN_PATH, S3Constants.S3_SETTINGS_PATH);
  }

  public String getForceVirtualHostAddressing() {
    return S3Constants.S3_FORCE_VIRTUAL_HOST_ADDRESSING;
  }
  public String getVerifyIntegrityAfterUpload(){
    return S3Constants.S3_VERIFY_INTEGRITY_AFTER_UPLOAD;
  }

  public String getEnableAccelerateMode() {
    return S3Constants.S3_ENABLE_ACCELERATE_MODE;
  }

  public String getPathPrefix() {
    return S3Constants.S3_PATH_PREFIX_SETTING;
  }

  public String getMultipartUploadThreshold() {
    return S3Constants.S3_MULTIPART_UPLOAD_THRESHOLD;
  }

  public String getMultipartUploadPartSize() {
    return S3Constants.S3_MULTIPART_MINIMUM_UPLOAD_PART_SIZE;
  }

  public String getCloudFrontEnabled() {
    return CloudFrontConstants.S3_CLOUDFRONT_ENABLED;
  }

  public String getCloudFrontSettingsGroup() {
    return CloudFrontConstants.S3_CLOUDFRONT_SETTINGS_GROUP;
  }

  public String getCloudFrontUploadDistribution() {
    return CloudFrontConstants.S3_CLOUDFRONT_UPLOAD_DISTRIBUTION;
  }

  public String getCloudFrontDownloadDistribution() {
    return CloudFrontConstants.S3_CLOUDFRONT_DOWNLOAD_DISTRIBUTION;
  }

  public String getCloudFrontPublicKeyId() {
    return CloudFrontConstants.S3_CLOUDFRONT_PUBLIC_KEY_ID;
  }

  public String getCloudFrontPrivateKey() {
    return CloudFrontConstants.S3_CLOUDFRONT_PRIVATE_KEY;
  }

  public String getCloudFrontCreateDistribution() {
    return CloudFrontConstants.S3_CLOUDFRONT_CREATE_DISTRIBUTIONS;
  }

  public List<String> getAclEnum() {
    return Arrays.stream(ObjectCannedACL.values()).map(ObjectCannedACL::name)
                 .collect(Collectors.toList());
  }

  public String getDefaultAcl() {
    return ObjectCannedACL.PRIVATE.name();
  }

  public String getTransferAccelerationEnabled() {
    return S3Constants.S3_TRANSFER_ACCELERATION_FEATURE_ENABLED;
  }

  public String getEnabledReactUi() {
    return S3Constants.S3_ENABLE_REACT_UI;
  }

  public String getChosenAwsConnectionId() {
    return AwsCloudConnectorConstants.CHOSEN_AWS_CONN_ID_PARAM;
  }
}
