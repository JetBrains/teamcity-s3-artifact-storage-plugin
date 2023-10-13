package jetbrains.buildServer.artifacts.s3;

import jetbrains.buildServer.artifacts.s3.amazonClient.AmazonS3Provider;
import jetbrains.buildServer.serverSide.ServerPaths;
import org.jetbrains.annotations.NotNull;

public class S3CompatibleArtifactContentProvider extends S3ArtifactContentProvider {
  public S3CompatibleArtifactContentProvider(@NotNull ServerPaths serverPaths,
                                             @NotNull AmazonS3Provider amazonS3Provider) {
    super(serverPaths, amazonS3Provider);
  }

  @NotNull
  @Override
  public String getType() {
    return S3Constants.S3_COMPATIBLE_STORAGE_TYPE;
  }
}
