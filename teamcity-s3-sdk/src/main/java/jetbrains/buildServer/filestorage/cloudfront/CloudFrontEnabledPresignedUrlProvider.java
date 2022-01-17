package jetbrains.buildServer.filestorage.cloudfront;

import com.amazonaws.HttpMethod;
import java.io.IOException;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface CloudFrontEnabledPresignedUrlProvider {
  @NotNull
  String generateDownloadUrl(@NotNull HttpMethod httpMethod,
                             @NotNull String objectKey,
                             @NotNull CloudFrontSettings settings) throws IOException;

  @NotNull
  String generateUploadUrl(@NotNull String objectKey, @Nullable String digest, @NotNull CloudFrontSettings settings) throws IOException;

  @NotNull
  String generateUploadUrlForPart(@NotNull String objectKey,
                                  @Nullable String digest,
                                  int nPart,
                                  @NotNull String uploadId,
                                  @NotNull CloudFrontSettings settings) throws IOException;

  void finishMultipartUpload(@NotNull String uploadId,
                             @NotNull String objectKey,
                             @NotNull CloudFrontSettings settings,
                             @Nullable String[] etags,
                             boolean isSuccessful) throws IOException;

  @NotNull
  String startMultipartUpload(@NotNull String objectKey, @NotNull CloudFrontSettings settings) throws Exception;

  @NotNull
  CloudFrontSettings settings(@NotNull Map<String, String> rawSettings, @NotNull RequestMetadata metadata);
}
