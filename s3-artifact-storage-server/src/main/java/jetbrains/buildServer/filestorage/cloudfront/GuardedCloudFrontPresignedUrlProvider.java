package jetbrains.buildServer.filestorage.cloudfront;

import java.io.IOException;
import jetbrains.buildServer.serverSide.IOGuard;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GuardedCloudFrontPresignedUrlProvider implements CloudFrontPresignedUrlProvider {
  private final CloudFrontPresignedUrlProvider myDelegate;

  public GuardedCloudFrontPresignedUrlProvider(@NotNull CloudFrontPresignedUrlProvider provider) {
    myDelegate = provider;
  }

  @Nullable
  @Override
  public String generateUrl(@NotNull String objectKey,
                            @NotNull CloudFrontSettings settings) throws IOException {
    return IOGuard.allowNetworkCall(() -> myDelegate.generateUrl(objectKey, settings));
  }

  @Override
  public String generateUrlForPart(@NotNull String objectKey, int nPart, @NotNull String uploadId, @NotNull CloudFrontSettings settings) throws IOException {
    return IOGuard.allowNetworkCall(() -> myDelegate.generateUrlForPart(objectKey, nPart, uploadId, settings));
  }
}
