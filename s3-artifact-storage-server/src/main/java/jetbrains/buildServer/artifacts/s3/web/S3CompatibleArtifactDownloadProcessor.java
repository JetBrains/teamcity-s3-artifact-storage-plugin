package jetbrains.buildServer.artifacts.s3.web;

import jetbrains.buildServer.ExtensionsProvider;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.cloudfront.CloudFrontEnabledPresignedUrlProvider;
import jetbrains.buildServer.serverSide.ProjectManagerEx;
import jetbrains.buildServer.web.ContentSecurityPolicyConfig;
import org.jetbrains.annotations.NotNull;

public class S3CompatibleArtifactDownloadProcessor extends S3ArtifactDownloadProcessor {

  public S3CompatibleArtifactDownloadProcessor(@NotNull CloudFrontEnabledPresignedUrlProvider preSignedUrlProvider,
                                               @NotNull ExtensionsProvider extensionsProvider,
                                               @NotNull ContentSecurityPolicyConfig contentSecurityPolicyConfig,
                                               @NotNull ProjectManagerEx projectManager) {
    super(preSignedUrlProvider, extensionsProvider, contentSecurityPolicyConfig, projectManager);
  }

  @NotNull
  @Override
  public String getType() {
    return S3Constants.S3_COMPATIBLE_STORAGE_TYPE;
  }
}
