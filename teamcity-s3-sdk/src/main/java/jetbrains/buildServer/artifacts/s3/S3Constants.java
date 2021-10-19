/*
 * Copyright 2000-2021 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
  public static final String S3_SETTINGS_PATH = "s3_storage_settings";
  public static final String S3_PLUGIN_PATH = "/plugins/s3-artifact-storage/";

  public static final String S3_PATH_PREFIX_ATTR = "s3_path_prefix";

  public static final String S3_URL_LIFETIME_SEC = "storage.s3.url.expiration.time.seconds";
  public static final String S3_USE_PRE_SIGNED_URL_FOR_UPLOAD = "storage.s3.upload.presignedUrl.enabled";
  public static final String S3_NUMBER_OF_RETRIES_ON_ERROR = "teamcity.internal.storage.s3.upload.numberOfRetries";
  public static final String S3_RETRY_DELAY_ON_ERROR_MS = "teamcity.internal.storage.s3.upload.retryDelayMs";
  public static final String S3_PRESIGNED_UPLOAD_MAX_NUMBER_OF_URLS_TO_LOAD_IN_ONE_REQUEST = "teamcity.internal.storage.s3.upload.presignedUrl.maxNumberOfUrlsToLoadInOneRequest";
  public static final String S3_PRESIGNED_UPLOAD_INTERNAL_CHUNK_SIZE = "teamcity.internal.storage.s3.upload.presignedUrl.internalChunkSize";
  public static final String S3_FORCE_VIRTUAL_HOST_ADDRESSING = "storage.s3.forceVirtualHostAddressing";
  public static final String S3_CLEANUP_BATCH_SIZE = "storage.s3.cleanup.batchSize";
  public static final String S3_MULTIPART_UPLOAD_THRESHOLD = "storage.s3.upload.multipart_threshold";
  public static final String S3_MULTIPART_MINIMUM_UPLOAD_PART_SIZE = "storage.s3.upload.multipart_chunksize";
  public static final String S3_ENABLE_PRESIGNED_MULTIPART_UPLOAD = "teamcity.internal.storage.s3.upload.presignedUrl.multipart.enabled";
  public static final String S3_CONNECTION_TIMEOUT = "teamcity.internal.storage.s3.upload.connectionTimeout";
  public static final String S3_NUMBER_OF_THREADS = "teamcity.internal.storage.s3.upload.numberOfThreads";
  public static final String S3_ENABLE_CONSISTENCY_CHECK = "teamcity.internal.storage.s3.upload.enableConsistencyCheck";

  public static final String S3_PATH_PREFIX_SETTING = "storage.s3.bucket.prefix";

  public static final String S3_URLS_PROVIDER_PATH = "storage.s3.presignedUrl.serverPath";

  public static final String ARTEFACTS_S3_UPLOAD_PRESIGN_URLS_HTML = "/artefacts/s3/upload/presign-urls.html";

  public static final String ERROR_SOURCE_HEADER_NAME = "X-SIGNED-URL-PROVIDER-ERROR-SOURCE";

  public static final String S3_REGION_HEADER_NAME = "s3-region";

  public static final String S3_AMAZON_METADATA_REQUEST_TIMEOUT_IN_SECONDS = "teamcity.internal.storage.s3.metadata.request.timeout.sec";

  public static enum ErrorSource {
    S3,
    SDK,
    TEAMCITY,
    UNKNOWN
  }
}
