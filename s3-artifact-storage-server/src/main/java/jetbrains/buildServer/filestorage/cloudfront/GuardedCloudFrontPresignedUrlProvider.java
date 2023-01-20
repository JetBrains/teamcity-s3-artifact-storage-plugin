package jetbrains.buildServer.filestorage.cloudfront;

import java.io.IOException;
import jetbrains.buildServer.artifacts.s3.cloudfront.CloudFrontPresignedUrlProvider;
import jetbrains.buildServer.artifacts.s3.cloudfront.CloudFrontSettings;
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
  public String generateDownloadUrl(@NotNull String objectKey,
                                    @NotNull CloudFrontSettings settings) throws IOException {
    return IOGuard.allowNetworkCall(() -> myDelegate.generateDownloadUrl(objectKey, settings));
  }

  @Nullable
  @Override
  public String generateUploadUrl(@NotNull String objectKey,
                                  @NotNull CloudFrontSettings settings) throws IOException {
    return IOGuard.allowNetworkCall(() -> myDelegate.generateUploadUrl(objectKey, settings));
  }

  @Override
  public String generateUploadUrlForPart(@NotNull String objectKey, int nPart, @NotNull String uploadId, @NotNull CloudFrontSettings settings) throws IOException {
    return IOGuard.allowNetworkCall(() -> myDelegate.generateUploadUrlForPart(objectKey, nPart, uploadId, settings));
  }
}
