

package jetbrains.buildServer.artifacts.s3;

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
  PresignedUrlWithTtl generateDownloadUrl(@NotNull HttpMethod httpMethod, @NotNull String objectKey, @NotNull S3Settings settings) throws IOException;

  @NotNull
  String generateUploadUrl(@NotNull String objectKey, @Nullable String digest, @NotNull S3Settings settings) throws IOException;

  @NotNull
  String generateUploadUrlForPart(@NotNull String objectKey, @Nullable String digest, int nPart, @NotNull String uploadId, @NotNull S3Settings settings) throws IOException;

  void finishMultipartUpload(@NotNull String uploadId, @NotNull String objectKey, @NotNull S3Settings settings, @Nullable String[] etags, boolean isSuccessful) throws IOException;

  @NotNull
  String startMultipartUpload(@NotNull String objectKey, @Nullable String contentType, @NotNull S3Settings settings) throws Exception;

  @NotNull
  S3Settings settings(@NotNull Map<String, String> rawSettings, @NotNull Map<String, String> projectSettings);

}
