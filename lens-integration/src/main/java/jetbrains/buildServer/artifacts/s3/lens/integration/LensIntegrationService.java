package jetbrains.buildServer.artifacts.s3.lens.integration;

import java.time.Duration;
import java.util.Collection;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.artifacts.s3.publish.UploadStatistics;
import jetbrains.buildServer.artifacts.s3.publish.presigned.upload.TeamCityConnectionConfiguration;
import org.jetbrains.annotations.NotNull;

public interface LensIntegrationService {
  void generateUploadEvents(@NotNull final AgentRunningBuild build,
                            @NotNull final Collection<UploadStatistics> statistics,
                            @NotNull final Duration totalUploadDuration,
                            @NotNull final TeamCityConnectionConfiguration teamCityConnectionConfiguration);
}
