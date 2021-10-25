package jetbrains.buildServer.artifacts.s3.publish.presigned.upload;

import java.util.Collection;
import jetbrains.buildServer.artifacts.ArtifactTransportAdditionalHeadersProvider;
import org.jetbrains.annotations.NotNull;

public interface PresignedUrlsProviderClientFactory {
  PresignedUrlsProviderClient createClient(@NotNull final TeamCityConnectionConfiguration connectionConfiguration,
                                           @NotNull Collection<ArtifactTransportAdditionalHeadersProvider> additionalHeadersProviders);
}
