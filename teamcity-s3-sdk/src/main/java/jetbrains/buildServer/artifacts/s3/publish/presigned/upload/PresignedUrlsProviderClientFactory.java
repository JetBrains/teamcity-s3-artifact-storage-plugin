package jetbrains.buildServer.artifacts.s3.publish.presigned.upload;

import org.jetbrains.annotations.NotNull;

public interface PresignedUrlsProviderClientFactory {
  PresignedUrlsProviderClient createClient(@NotNull final TeamCityConnectionConfiguration connectionConfiguration);
}
