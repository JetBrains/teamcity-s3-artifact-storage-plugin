package jetbrains.buildServer.artifacts.s3.orphans;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

class BuildEntry {
  private final String myPath;
  private final String myId;

  BuildEntry(@NotNull String path, @NotNull String id) {
    myPath = path;
    myId = id;
  }

  @NotNull
  String getId() {
    return myId;
  }

  @NotNull
  String getPath() {
    return myPath;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BuildEntry that = (BuildEntry) o;
    return Objects.equals(myId, that.myId) && Objects.equals(myPath, that.myPath);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myPath, myId);
  }

  @Override
  public String toString() {
    return "BuildEntry{" +
      "myPath='" + myPath + '\'' +
      ", myId=" + myId +
      '}';
  }
}
