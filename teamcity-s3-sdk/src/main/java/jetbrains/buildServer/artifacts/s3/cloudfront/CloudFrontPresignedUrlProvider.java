package jetbrains.buildServer.artifacts.s3.cloudfront;

import java.io.IOException;
import jetbrains.buildServer.artifacts.s3.PresignedUrlWithTtl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface CloudFrontPresignedUrlProvider {

  @Nullable
  PresignedUrlWithTtl generateDownloadUrl(@NotNull String objectKey, @NotNull CloudFrontSettings settings) throws IOException;

  String generateUploadUrl(@NotNull String objectKey, @NotNull CloudFrontSettings settings) throws IOException;

  String generateUploadUrlForPart(@NotNull String objectKey, int nPart, @NotNull String uploadId, @NotNull CloudFrontSettings settings) throws IOException;
}
