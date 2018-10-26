package jetbrains.buildServer.artifacts.s3;

/**
 * Created by Nikita.Skvortsov
 * date: 08.02.2016.
 */
public class S3Constants {

  public static final String S3_BUCKET_NAME = "storage.s3.bucket.name";

  public static final String S3_STORAGE_TYPE = "S3_storage";
  public static final String S3_SETTINGS_PATH = "s3_storage_settings";

  public static final String S3_PATH_PREFIX_ATTR = "s3_path_prefix";

  public static final String S3_URL_LIFETIME_SEC = "storage.s3.url.expiration.time.seconds";
  public static final String S3_USE_PRE_SIGNED_URL_FOR_UPLOAD = "storage.s3.upload.presignedUrl.enabled";
  public static final String S3_USE_SIGNATURE_V4 = "storage.s3.use.signature.v4";
  public static final String S3_CLEANUP_BATCH_SIZE = "storage.s3.cleanup.batchSize";
  public static final String S3_CLEANUP_USE_PARALLEL = "storage.s3.cleanup.useParallel";

  public static final int DEFAULT_S3_URL_LIFETIME_SEC = 60;

  public static final String ARTEFACTS_S3_UPLOAD_PRESIGN_URLS_HTML = "/artefacts/s3/upload/presign-urls.html";
}
