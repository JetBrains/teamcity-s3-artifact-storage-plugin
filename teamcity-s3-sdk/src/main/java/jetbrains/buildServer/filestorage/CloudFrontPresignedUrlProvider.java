package jetbrains.buildServer.filestorage;

import java.io.IOException;
import java.util.Map;
import jetbrains.buildServer.ssh.TeamCitySshKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface CloudFrontPresignedUrlProvider{

  @NotNull
  String generateDownloadUrl(@NotNull String objectKey, @NotNull TeamCitySshKey privateKey,
                             @NotNull CloudFrontSettings settings) throws IOException;

  @NotNull
  CloudFrontSettings settings(@NotNull Map<String, String> rawSettings);

  interface CloudFrontSettings extends S3PresignedUrlProvider.S3Settings {
    boolean getCloudFrontEnabled();

    @Nullable
    String getCloudFrontDomain();

    @Nullable
    String getCloudFrontPublicKeyId();

    @NotNull
    String getCloudFrontPrivateKey();

    @NotNull
    String getBucketRegion();
  }
}
