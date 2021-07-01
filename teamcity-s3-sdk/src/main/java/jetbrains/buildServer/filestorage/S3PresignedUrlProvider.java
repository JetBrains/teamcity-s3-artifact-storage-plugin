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

package jetbrains.buildServer.filestorage;

import com.amazonaws.HttpMethod;
import java.io.IOException;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by Evgeniy Koshkin (evgeniy.koshkin@jetbrains.com) on 19.07.17.
 */
public interface S3PresignedUrlProvider {

  @NotNull
  String generateDownloadUrl(@NotNull HttpMethod httpMethod, @NotNull String objectKey, @NotNull S3Settings settings) throws IOException;

  @NotNull
  String generateUploadUrl(@NotNull String objectKey, @NotNull S3Settings settings) throws IOException;

  @NotNull
  String generateUploadUrlForPart(@NotNull String objectKey, int nPart, @NotNull String uploadId, @NotNull S3Settings settings) throws IOException;

  void finishMultipartUpload(@NotNull String uploadId, @NotNull String objectKey, @NotNull S3Settings settings, @Nullable String[] etags, boolean isSuccessful) throws IOException;

  @NotNull
  String startMultipartUpload(@NotNull String objectKey, @NotNull S3Settings settings) throws Exception;

  @NotNull
  S3Settings settings(@NotNull Map<String, String> rawSettings);

  interface S3Settings {
    @NotNull
    String getBucketName();

    int getUrlTtlSeconds();
  }
}
