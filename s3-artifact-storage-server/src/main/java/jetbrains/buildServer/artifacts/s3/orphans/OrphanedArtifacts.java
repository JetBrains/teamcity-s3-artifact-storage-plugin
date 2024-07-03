package jetbrains.buildServer.artifacts.s3.orphans;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public class OrphanedArtifacts {
  private final Collection<OrphanedArtifact> orphanedPaths;
  private final List<String> errors;

  OrphanedArtifacts(@NotNull Collection<OrphanedArtifact> orphanedPaths, @NotNull List<String> errors) {
    this.orphanedPaths = orphanedPaths;
    this.errors = errors;
  }

  public Collection<OrphanedArtifact> getOrphanedPaths() {
    return orphanedPaths;
  }

  public List<String> getErrors() {
    return errors;
  }
}
