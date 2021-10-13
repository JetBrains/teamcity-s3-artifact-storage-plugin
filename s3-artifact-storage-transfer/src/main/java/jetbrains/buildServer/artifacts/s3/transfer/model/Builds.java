package jetbrains.buildServer.artifacts.s3.transfer.model;

import java.util.List;
import java.util.Objects;

/**
 * Represents a paginated list of Build entities.
 */
public class Builds {
  private List<Build> build = null;

  public Builds build(List<Build> build) {

    this.build = build;
    return this;
  }

  public List<Build> getBuild() {
    return build;
  }

  public void setBuild(List<Build> build) {
    this.build = build;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Builds builds = (Builds)o;
    return Objects.equals(build, builds.build);
  }

  @Override
  public int hashCode() {
    return Objects.hash(build);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Builds {\n");
    sb.append("    build: ").append(toIndentedString(build)).append("\n");
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

