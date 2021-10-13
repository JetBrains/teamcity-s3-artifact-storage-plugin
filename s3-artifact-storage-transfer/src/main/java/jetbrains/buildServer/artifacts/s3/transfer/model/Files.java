package jetbrains.buildServer.artifacts.s3.transfer.model;

import java.util.List;
import java.util.Objects;

/**
 * Represents a list of File entities.
 */
public class Files {
  private List<File> file = null;

  public Files file(List<File> file) {

    this.file = file;
    return this;
  }

  public List<File> getFile() {
    return file;
  }

  public void setFile(List<File> file) {
    this.file = file;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Files files = (Files)o;
    return Objects.equals(this.file, files.file);
  }

  @Override
  public int hashCode() {
    return Objects.hash(file);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Files {\n");
    sb.append("    file: ").append(toIndentedString(file)).append("\n");
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

