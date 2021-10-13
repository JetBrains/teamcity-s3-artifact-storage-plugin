package jetbrains.buildServer.artifacts.s3.transfer.model;

import java.util.List;

public class BuildArtifacts {

  private final Build metadata;
  private final List<String> artifacts;

  public BuildArtifacts(Build metadata, List<String> artifacts) {
    this.metadata = metadata;
    this.artifacts = artifacts;
  }

  public Build getMetadata() {
    return metadata;
  }

  public List<String> getArtifacts() {
    return artifacts;
  }

}
