package jetbrains.buildServer.artifacts.s3.web;

import jetbrains.buildServer.artifacts.s3.S3Constants;

public class S3ParametersProvider {
  public String getBucketName() {
    return S3Constants.S3_BUCKET_NAME;
  }

  public String getBucketNameProvidedAsString() {
    return S3Constants.S3_BUCKET_NAME_PROVIDED_AS_STRING;
  }

  public String getBucketNameWasProvidedAsString() {
    return S3Constants.S3_BUCKET_NAME_WAS_PROVIDED_AS_STRING;
  }

  public String getUsePresignUrlsForUpload() {
    return S3Constants.S3_USE_PRE_SIGNED_URL_FOR_UPLOAD;
  }

  public String getUseSignatureVersion4() {
    return S3Constants.S3_USE_SIGNATURE_V4;
  }

  public String getContainersPath() {
    return String.format("/plugins/s3-artifact-storage/%s.html", S3Constants.S3_SETTINGS_PATH);
  }
}
