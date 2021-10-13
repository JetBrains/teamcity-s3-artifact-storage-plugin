package jetbrains.buildServer.artifacts.s3.transfer.model;

import java.util.List;
import java.util.Objects;

/**
 * Represents a paginated list of BuildType entities.
 */
public class BuildTypes {

  private List<BuildType> buildType = null;

  public List<BuildType> getBuildType() {
    return buildType;
  }

  public void setBuildType(List<BuildType> buildType) {
    this.buildType = buildType;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BuildTypes buildTypes = (BuildTypes)o;
    return Objects.equals(this.buildType, buildTypes.buildType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(buildType);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class BuildTypes {\n");
    sb.append("    buildType: ").append(toIndentedString(buildType)).append("\n");
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

