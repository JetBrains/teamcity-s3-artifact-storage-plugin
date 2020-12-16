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

package jetbrains.buildServer.artifacts.s3.preSignedUrl;

import com.amazonaws.HttpMethod;
import java.io.IOException;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by Evgeniy Koshkin (evgeniy.koshkin@jetbrains.com) on 19.07.17.
 */
public interface S3PreSignedManager {
  @NotNull
  String generateUrl(@NotNull HttpMethod httpMethod, @NotNull String objectKey, @NotNull Map<String, String> params) throws IOException;

  @NotNull
  String generateUrlForPart(@NotNull HttpMethod httpMethod, @NotNull String objectKey, final int nPart, @NotNull final String uploadId, @NotNull Map<String, String> params)
    throws IOException;

  void finishMultipartUpload(@NotNull String uploadId, @NotNull String objectKey, @NotNull Map<String, String> params, @Nullable final String[] etags, boolean isSuccessful)
    throws Exception;

  @NotNull
  String startMultipartUpload(@NotNull String objectKey, @NotNull Map<String, String> params) throws Exception;
}
