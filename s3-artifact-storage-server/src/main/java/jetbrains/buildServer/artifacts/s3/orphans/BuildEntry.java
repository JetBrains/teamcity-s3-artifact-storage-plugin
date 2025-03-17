package jetbrains.buildServer.artifacts.s3.orphans;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

class BuildEntry {
  private final String myPath;
  private final long myId;

  BuildEntry(@NotNull String path, long id) {
    myPath = path;
    myId = id;
  }

  long getId() {
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
    return myId == that.myId && Objects.equals(myPath, that.myPath);
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
