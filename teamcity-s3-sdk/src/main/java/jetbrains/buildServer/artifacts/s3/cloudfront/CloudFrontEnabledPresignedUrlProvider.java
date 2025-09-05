package jetbrains.buildServer.artifacts.s3.cloudfront;

import java.io.IOException;
import java.util.Map;

import jetbrains.buildServer.artifacts.s3.PresignedUrlWithTtl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.amazon.awssdk.http.SdkHttpMethod;

public interface CloudFrontEnabledPresignedUrlProvider {
  @NotNull
  PresignedUrlWithTtl generateDownloadUrl(@NotNull SdkHttpMethod httpMethod,
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
  String startMultipartUpload(@NotNull String objectKey, @Nullable String contentType, @NotNull CloudFrontSettings settings) throws Exception;

  @NotNull
  CloudFrontSettings settings(@NotNull Map<String, String> rawSettings, @NotNull Map<String, String> projectSettings, @NotNull RequestMetadata metadata);
}
