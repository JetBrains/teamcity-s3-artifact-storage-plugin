package jetbrains.buildServer.filestorage.cloudfront;

import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface CloudFrontPresignedUrlProvider {

  @Nullable
  String generateDownloadUrl(@NotNull String objectKey, @NotNull CloudFrontSettings settings) throws IOException;

  String generateUploadUrl(@NotNull String objectKey, @NotNull CloudFrontSettings settings) throws IOException;

  String generateUploadUrlForPart(@NotNull String objectKey, int nPart, @NotNull String uploadId, @NotNull CloudFrontSettings settings) throws IOException;
}
