package jetbrains.buildServer.artifacts.s3.transfer.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

@JsonDeserialize(using = BuildDeserializer.class)
public class Build {
  @NotNull
  private final String id;
  @NotNull
  private final String projectId;
  @NotNull
  private final String buildTypeId;
  @NotNull
  private final String buildTypeName;
  @NotNull
  private final String directory;
  @NotNull
  private final List<String> artifacts;

  public Build(@NotNull String id,
               @NotNull String projectId,
               @NotNull String buildTypeId,
               @NotNull String buildTypeName,
               @NotNull String directory,
               @NotNull ArrayList<String> artifacts) {
    this.id = id;
    this.projectId = projectId;
    this.buildTypeId = buildTypeId;
    this.buildTypeName = buildTypeName;
    this.directory = directory;
    this.artifacts = artifacts;
  }

  @NotNull
  public String getBuildTypeId() {
    return buildTypeId;
  }

  @NotNull
  public String getBuildTypeName() {
    return buildTypeName;
  }

  @NotNull
  public String getDirectory() {
    return directory;
  }

  @NotNull
  public List<String> getArtifacts() {
    return artifacts;
  }

  @NotNull
  public String getId() {
    return id;
  }

  @NotNull
  public String getProjectId() {
    return projectId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Build build = (Build)o;
    return Objects.equals(id, build.id) && Objects.equals(projectId, build.projectId) && Objects.equals(buildTypeId, build.buildTypeId) &&
           Objects.equals(buildTypeName, build.buildTypeName) && Objects.equals(directory, build.directory) &&
           Objects.equals(artifacts, build.artifacts);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, projectId, buildTypeId, buildTypeName, directory, artifacts);
  }

  @Override
  public String toString() {
    return "Build{" +
           "id='" + id + '\'' +
           ", projectId='" + projectId + '\'' +
           ", buildTypeId='" + buildTypeId + '\'' +
           ", buildTypeName='" + buildTypeName + '\'' +
           ", directory='" + directory + '\'' +
           ", artifacts=" + artifacts +
           '}';
  }
}

