package jetbrains.buildServer.artifacts.s3.publish;

import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.artifacts.ArtifactDataInstance;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.Map;

public interface S3FileUploader {
  @NotNull
  Collection<ArtifactDataInstance> publishFiles(@NotNull final AgentRunningBuild build,
                                                @NotNull final String pathPrefix,
                                                @NotNull final Map<File, String> filesToPublish);
}
