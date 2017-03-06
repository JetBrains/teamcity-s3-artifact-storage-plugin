package jetbrains.buildServer.artifacts.s3;

import jetbrains.buildServer.ArtifactsConstants;

/**
 * Created by Nikita.Skvortsov
 * date: 08.02.2016.
 */
public class S3Constants {

  public static final String EXTERNAL_ARTIFACTS_LIST = ArtifactsConstants.TEAMCITY_ARTIFACTS_DIR + "/" + ArtifactsConstants.EXTERNAL_ARTIFACTS_LIST_NAME;

  public static final String S3_BUCKET_NAME = "storage.s3.bucket.name";

  public static final String S3_PATH_PREFIX_SYSTEM_PROPERTY = "storage.s3.path.prefix";

  public static final String S3_STORAGE_TYPE = "S3_storage";

  public static final String S3_KEY_ATTR = "S3_KEY";
  public static final String S3_BUCKET_ATTR = "S3_BUCKET";

  public static final String S3_ACCESS_CONTROLLER_PATH = "/s3access.html";

  public static final String S3_URL_LIFETIME_SEC = "storage.s3.url.expiration.time.seconds";
  public static final int DEFAULT_S3_URL_LIFETIME_SEC = 60;
}
