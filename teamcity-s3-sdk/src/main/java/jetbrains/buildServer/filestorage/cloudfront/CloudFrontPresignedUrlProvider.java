package jetbrains.buildServer.filestorage.cloudfront;

import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface CloudFrontPresignedUrlProvider {

  @Nullable
  String generateUrl(@NotNull String objectKey, @NotNull CloudFrontSettings settings) throws IOException;

  String generateUrlForPart(@NotNull String objectKey, int nPart, @NotNull String uploadId, @NotNull CloudFrontSettings settings) throws IOException;
}
