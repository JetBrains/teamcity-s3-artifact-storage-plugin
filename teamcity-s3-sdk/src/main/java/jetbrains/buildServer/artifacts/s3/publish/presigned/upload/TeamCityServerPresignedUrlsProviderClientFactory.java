package jetbrains.buildServer.artifacts.s3.publish.presigned.upload;

import org.jetbrains.annotations.NotNull;

public class TeamCityServerPresignedUrlsProviderClientFactory implements PresignedUrlsProviderClientFactory {
  @Override
  public PresignedUrlsProviderClient createClient(@NotNull TeamCityConnectionConfiguration teamCityConnectionConfiguration) {
    return new TeamCityServerPresignedUrlsProviderClient(teamCityConnectionConfiguration);
  }
}
