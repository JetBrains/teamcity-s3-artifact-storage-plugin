/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

  public static final String S3_BUCKET_NAME = "storage.s3.bucket.name";
  public static final String S3_BUCKET_NAME_WAS_PROVIDED_AS_STRING = "storage.s3.bucket.name.wasProvidedAsString";

  public static final String S3_STORAGE_TYPE = "S3_storage";
  public static final String S3_SETTINGS_PATH = "s3_storage_settings";

  public static final String S3_PATH_PREFIX_ATTR = "s3_path_prefix";

  public static final String S3_URL_LIFETIME_SEC = "storage.s3.url.expiration.time.seconds";
  public static final String S3_USE_PRE_SIGNED_URL_FOR_UPLOAD = "storage.s3.upload.presignedUrl.enabled";
  public static final String S3_NUMBER_OF_RETRIES_ON_ERROR = "teamcity.internal.storage.s3.upload.numberOfRetries";
  public static final String S3_RETRY_DELAY_MS_ON_ERROR = "teamcity.internal.storage.s3.upload.retryDelayMs";
  public static final String S3_USE_SIGNATURE_V4 = "storage.s3.use.signature.v4";
  public static final String S3_FORCE_VIRTUAL_HOST_ADDRESSING = "storage.s3.forceVirtualHostAddressing";
  public static final String S3_CLEANUP_BATCH_SIZE = "storage.s3.cleanup.batchSize";
  public static final String S3_MULTIPART_UPLOAD_THRESHOLD = "storage.s3.upload.multipart.uploadThreshold";
  public static final String S3_MULTIPART_MINIMUM_UPLOAD_PART_SIZE = "storage.s3.upload.multipart.minimumUploadPartSize";

  public static final String S3_PATH_PREFIX_SETTING = "storage.s3.bucket.prefix";

  public static final int DEFAULT_S3_URL_LIFETIME_SEC = 60;
  public static final int DEFAULT_S3_RETRY_DELAY_ON_ERROR_MS = 0;
  public static final int DEFAULT_S3_NUMBER_OF_RETRIES_ON_ERROR = 5;

  public static final String ARTEFACTS_S3_UPLOAD_PRESIGN_URLS_HTML = "/artefacts/s3/upload/presign-urls.html";
}
