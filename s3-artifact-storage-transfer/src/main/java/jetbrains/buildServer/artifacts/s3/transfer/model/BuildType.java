package jetbrains.buildServer.artifacts.s3.transfer.model;

import java.util.Objects;

/**
 * Represents a build configuration.
 */
public class BuildType {
  private String id;
  private String name;
  private String projectId;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getProjectId() {
    return projectId;
  }

  public void setProjectId(String projectId) {
    this.projectId = projectId;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BuildType buildType = (BuildType)o;
    return Objects.equals(id, buildType.id) && Objects.equals(name, buildType.name) && Objects.equals(projectId, buildType.projectId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, projectId);
  }

  @Override
  public String toString() {
    return "BuildType{" +
           "id='" + id + '\'' +
           ", name='" + name + '\'' +
           ", projectId='" + projectId + '\'' +
           '}';
  }
}

