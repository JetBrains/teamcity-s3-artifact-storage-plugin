package jetbrains.buildServer.artifacts.s3.transfer.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonDeserialize(using = ProjectDeserializer.class)
public class Project {
  private final List<Feature> features;
  private final List<String> subprojects;
  private String id;
  private String name;

  public Project(String id, String name, List<Feature> features, ArrayList<String> subprojects) {
    this.id = id;
    this.name = name;
    this.features = features;
    this.subprojects = subprojects;
  }

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

  public List<Feature> getFeatures() {
    return features;
  }

  public List<String> getSubprojects() {
    return subprojects;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Project project = (Project)o;
    return Objects.equals(features, project.features) && Objects.equals(subprojects, project.subprojects) && Objects.equals(id, project.id) &&
           Objects.equals(name, project.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(features, subprojects, id, name);
  }

  @Override
  public String toString() {
    return "Project{" +
           "features=" + features +
           ", subprojects=" + subprojects +
           ", id='" + id + '\'' +
           ", name='" + name + '\'' +
           '}';
  }
}

