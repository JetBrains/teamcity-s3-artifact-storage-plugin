package jetbrains.buildServer.artifacts.s3.transfer.model;

import java.util.List;
import org.jetbrains.annotations.NotNull;

public class BuildArtifacts {
  @NotNull
  private final Build metadata;
  @NotNull
  private final List<String> artifacts;

  public BuildArtifacts(@NotNull Build metadata, @NotNull List<String> artifacts) {
    this.metadata = metadata;
    this.artifacts = artifacts;
  }

  @NotNull
  public Build getMetadata() {
    return metadata;
  }

  @NotNull
  public List<String> getArtifacts() {
    return artifacts;
  }

}
