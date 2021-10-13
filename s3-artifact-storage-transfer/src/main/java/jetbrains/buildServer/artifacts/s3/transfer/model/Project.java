package jetbrains.buildServer.artifacts.s3.transfer.model;

import java.util.Objects;

/**
 * Represents a project.
 */
//TODO introduce custom deserializer to avoid having all these intermediate classes
public class Project {
  private String id;
  private String name;
  private BuildTypes buildTypes;
  private Properties parameters;
  private ProjectFeatures projectFeatures;
  private Projects projects;

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

  public BuildTypes getBuildTypes() {
    return buildTypes;
  }

  public void setBuildTypes(BuildTypes buildTypes) {
    this.buildTypes = buildTypes;
  }

  public Properties getParameters() {
    return parameters;
  }

  public void setParameters(Properties parameters) {
    this.parameters = parameters;
  }

  public ProjectFeatures getProjectFeatures() {
    return projectFeatures;
  }

  public void setProjectFeatures(ProjectFeatures projectFeatures) {
    this.projectFeatures = projectFeatures;
  }

  public Projects getProjects() {
    return projects;
  }

  public void setProjects(Projects projects) {
    this.projects = projects;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Project project = (Project)o;
    return Objects.equals(id, project.id) && Objects.equals(name, project.name) && Objects.equals(buildTypes, project.buildTypes) &&
           Objects.equals(parameters, project.parameters) && Objects.equals(projectFeatures, project.projectFeatures) &&
           Objects.equals(projects, project.projects);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, buildTypes, parameters, projectFeatures, projects);
  }

  @Override
  public String toString() {
    return "Project{" +
           "id='" + id + '\'' +
           ", name='" + name + '\'' +
           ", buildTypes=" + buildTypes +
           ", parameters=" + parameters +
           ", projectFeatures=" + projectFeatures +
           ", projects=" + projects +
           '}';
  }
}

