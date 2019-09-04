package jetbrains.buildServer.artifacts.s3.web;

import jetbrains.buildServer.artifacts.s3.S3Constants;

public class S3ParametersProvider {
  public String getBucketName() {
    return S3Constants.S3_BUCKET_NAME;
  }

  public String getUsePresignUrlsForUpload(){
    return S3Constants.S3_USE_PRE_SIGNED_URL_FOR_UPLOAD;
  }

  public String getUseSignatureVersion4(){
    return S3Constants.S3_USE_SIGNATURE_V4;
  }

  public String getRetryDelayOnErrorMs() {
    return S3Constants.S3_RETRY_DELAY_MS_ON_ERROR;
  }

  public String getNumberOfRetriesOnError(){
    return S3Constants.S3_NUMBER_OF_RETRIES_ON_ERROR;
  }

  public String getContainersPath() {
    return String.format("/plugins/s3-artifact-storage/%s.html", S3Constants.S3_SETTINGS_PATH);
  }
}
