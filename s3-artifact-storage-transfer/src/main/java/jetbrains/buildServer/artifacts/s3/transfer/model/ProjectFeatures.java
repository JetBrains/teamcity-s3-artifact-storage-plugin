package jetbrains.buildServer.artifacts.s3.transfer.model;

import java.util.List;
import java.util.Objects;

/**
 * Represents a list of ProjectFeature entities.
 */
public class ProjectFeatures {
  private List<Feature> projectFeature = null;

  public List<Feature> getProjectFeature() {
    return projectFeature;
  }


  public void setProjectFeature(List<Feature> projectFeature) {
    this.projectFeature = projectFeature;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ProjectFeatures projectFeatures = (ProjectFeatures)o;
    return
      Objects.equals(this.projectFeature, projectFeatures.projectFeature);
  }

  @Override
  public int hashCode() {
    return Objects.hash(projectFeature);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ProjectFeatures {\n");
    sb.append("    projectFeature: ").append(toIndentedString(projectFeature)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }

}

