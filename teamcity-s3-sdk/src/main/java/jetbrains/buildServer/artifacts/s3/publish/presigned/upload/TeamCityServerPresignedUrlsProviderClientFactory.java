package jetbrains.buildServer.artifacts.s3.publish.presigned.upload;

import java.util.Collection;
import jetbrains.buildServer.artifacts.ArtifactTransportAdditionalHeadersProvider;
import org.jetbrains.annotations.NotNull;

public class TeamCityServerPresignedUrlsProviderClientFactory implements PresignedUrlsProviderClientFactory {
  @Override
  public PresignedUrlsProviderClient createClient(@NotNull TeamCityConnectionConfiguration teamCityConnectionConfiguration,
                                                  @NotNull Collection<ArtifactTransportAdditionalHeadersProvider> additionalHeadersProviders) {
    return new TeamCityServerPresignedUrlsProviderClient(teamCityConnectionConfiguration, additionalHeadersProviders);
  }
}
