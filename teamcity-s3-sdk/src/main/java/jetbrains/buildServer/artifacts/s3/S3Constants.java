

package jetbrains.buildServer.artifacts.s3;

/**
 * Created by Nikita.Skvortsov
 * date: 08.02.2016.
 */
public class S3Constants {

  public static final String S3_ADDRESS = "s3.amazonaws.com";

  public static final String S3_BUCKET_NAME = "storage.s3.bucket.name";
  public static final String S3_BUCKET_NAME_WAS_PROVIDED_AS_STRING = "storage.s3.bucket.name.wasProvidedAsString";

  public static final String S3_STORAGE_TYPE = "S3_storage";
  public static final String S3_COMPATIBLE_STORAGE_TYPE = "S3_storage_compatible";
  public static final String S3_SETTINGS_PATH = "s3_storage_settings";
  public static final String S3_PLUGIN_PATH = "/plugins/s3-artifact-storage/";

  public static final String S3_PATH_PREFIX_ATTR = "s3_path_prefix";

  public static final String S3_URL_LIFETIME_SEC = "storage.s3.url.expiration.time.seconds";
  public static final String S3_URL_LIFETIME_EXTENDED_SEC = "storage.s3.url.expiration.time.extended.seconds";
  public static final String S3_USE_PRE_SIGNED_URL_FOR_UPLOAD = "storage.s3.upload.presignedUrl.enabled";
  public static final String S3_NUMBER_OF_RETRIES_ON_ERROR = "teamcity.internal.storage.s3.upload.numberOfRetries";
  public static final String S3_RETRY_DELAY_ON_ERROR_MS = "teamcity.internal.storage.s3.upload.retryDelayMs";
  public static final String S3_PRESIGNED_UPLOAD_MAX_NUMBER_OF_URLS_TO_LOAD_IN_ONE_REQUEST = "teamcity.internal.storage.s3.upload.presignedUrl.maxNumberOfUrlsToLoadInOneRequest";
  public static final String S3_PRESIGNED_UPLOAD_INTERNAL_CHUNK_SIZE = "teamcity.internal.storage.s3.upload.presignedUrl.internalChunkSize";
  public static final String S3_FORCE_VIRTUAL_HOST_ADDRESSING = "storage.s3.forceVirtualHostAddressing";

  public static final String S3_VERIFY_INTEGRITY_AFTER_UPLOAD = "storage.s3.verifyIntegrityAfterUpload";

  public static final String S3_TRANSFER_ACCELERATION_FEATURE_ENABLED = "teamcity.s3.transfer.acceleration.enabled";
  public static final String S3_ENABLE_ACCELERATE_MODE = "storage.s3.accelerateModeEnabled";
  public static final String S3_CLEANUP_BATCH_SIZE = "storage.s3.cleanup.batchSize";
  public static final String S3_MULTIPART_UPLOAD_THRESHOLD = "storage.s3.upload.multipart_threshold";
  public static final String S3_MULTIPART_MINIMUM_UPLOAD_PART_SIZE = "storage.s3.upload.multipart_chunksize";
  public static final String S3_ENABLE_PRESIGNED_MULTIPART_UPLOAD = "teamcity.internal.storage.s3.upload.presignedUrl.multipart.enabled";
  public static final String S3_CONNECTION_TIMEOUT = "teamcity.internal.storage.s3.upload.connectionTimeout";
  public static final String S3_NUMBER_OF_THREADS_FOR_FILES = "teamcity.internal.storage.s3.upload.numberOfThreads";
  public static final String S3_NUMBER_OF_THREADS_FOR_FILE_PARTS = "teamcity.internal.storage.s3.upload.numberOfThreadsPerFile";
  public static final String S3_ENABLE_CONSISTENCY_CHECK = "teamcity.internal.storage.s3.upload.enableConsistencyCheck";
  public static final String S3_PARALLEL_DOWNLOAD_ENABLED = "teamcity.internal.storage.s3.download.parallel.enabled"; // feature toggle
  public static final String S3_PARALLEL_DOWNLOAD_FORCED = "teamcity.internal.storage.s3.download.parallel.forced"; // for all types of artifact storages (development, experiments)
  public static final String S3_PARALLEL_DOWNLOAD_MAX_THREADS = "teamcity.internal.storage.s3.download.parallel.maxThreads";
  public static final String S3_PARALLEL_DOWNLOAD_MIN_PART_SIZE_MB = "teamcity.internal.storage.s3.download.parallel.minPartSizeMB";
  public static final String S3_PARALLEL_DOWNLOAD_MAX_FILE_SIZE_GB = "teamcity.internal.storage.s3.download.parallel.maxFileSizeGB"; // workaround until TW-90851 is fixed
  public static final String S3_PARALLEL_DOWNLOAD_BUFFER_SIZE_KB = "teamcity.internal.storage.s3.download.parallel.bufferSizeKB";
  public static final String S3_PARALLEL_DOWNLOAD_STRATEGY = "teamcity.internal.storage.s3.download.parallel.strategy";
  public static final String S3_PARALLEL_DOWNLOAD_MAX_CONNECTIONS = "teamcity.internal.storage.s3.download.parallel.maxConnections";
  public static final String S3_PARALLEL_DOWNLOAD_MAX_CONNECTIONS_PER_HOST = "teamcity.internal.storage.s3.download.parallel.maxConnectionsPerHost";
  public static final String S3_ACL = "storage.s3.acl";
  public static final String S3_ARTIFACT_KEYS_HEADER_MAX_NUMBER = "teamcity.internal.storage.s3.url.request.keys.header.max.number";

  public static final String S3_PATH_PREFIX_SETTING = "storage.s3.bucket.prefix";

  public static final String S3_URLS_PROVIDER_PATH = "storage.s3.presignedUrl.serverPath";

  public static final String ARTEFACTS_S3_UPLOAD_PRESIGN_URLS_HTML = "/artefacts/s3/upload/presign-urls.html";

  public static final String ERROR_SOURCE_HEADER_NAME = "X-SIGNED-URL-PROVIDER-ERROR-SOURCE";

  public static final String S3_REGION_HEADER_NAME = "s3-region";
  public static final String S3_ARTIFACT_KEYS_HEADER_NAME = "s3-url-request-artifact-keys";

  public static final String S3_CUSTOM_TTL_HEADER_NAME = "s3-url-request-custom-ttl";

  public static final String S3_AMAZON_METADATA_REQUEST_TIMEOUT_IN_SECONDS = "teamcity.internal.storage.s3.metadata.request.timeout.sec";

  public static final long S3_AMAZON_REQUEST_TIMEOUT_CAP_IN_SECONDS = 24 * 60 * 60; // 24 hours

  public static final String S3_ENABLE_MULTIPART_COMPLETION_RETRY =
    "teamcity.internal.storage.s3.multipart.completion.enableRetry";
  public static final String S3_ENABLE_REACT_UI = "teamcity.internal.storage.s3.ui.react.enabled";
  public static final String ALLOW_HTTP_CONNECTION_FOR_UPLOAD = "teamcity.internal.storage.s3.allow.plain.http";
  public static final String S3_FORCE_PRESIGNED_URLS = "teamcity.internal.storage.s3.force.presigned.urls";

  public static final String S3_ENABLE_UPLOAD_FILE_LENS_EVENTS = "teamcity.internal.storage.s3.lens.file.events.enabled";
  public static final String S3_ENABLE_UPLOAD_INFO_LENS_EVENTS = "teamcity.internal.storage.s3.lens.info.events.enabled";

  public static final String PROJECT_ID_PARAM = "projectId";

  public static final String TEAMCITY_STORAGE_TYPE_KEY = "storage.type";
  public static final String S3_DOWNLOAD_THRESHOLD_FOR_PRESIGN_URL_EXTENSION_IN_GB = "storage.s3.download.threshold.presigned.url.extended"; // if file in S3 is bigger than this, extended ttl for presigned url should be used

  public enum ErrorSource {
    S3,
    SDK,
    TEAMCITY,
    UNKNOWN
  }
}
