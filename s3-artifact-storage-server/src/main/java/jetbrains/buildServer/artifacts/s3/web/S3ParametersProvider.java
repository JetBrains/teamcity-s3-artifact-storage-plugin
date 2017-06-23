package jetbrains.buildServer.artifacts.s3.web;

import jetbrains.buildServer.artifacts.s3.S3Constants;

public class S3ParametersProvider {
  public String getBucketName() {
    return S3Constants.S3_BUCKET_NAME;
  }

  public String getContainersPath() {
    return String.format("/plugins/s3-artifact-storage/%s.html", S3Constants.S3_SETTINGS_PATH);
  }
}
