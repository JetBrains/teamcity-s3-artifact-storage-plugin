package jetbrains.buildServer.artifacts.s3.orphans;

import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

class BuildTypeEntry {
  private final String myPath;
  private final Set<BuildEntry> myBuildEntries;
  private final boolean myOutdated;

  BuildTypeEntry(@NotNull String path, @NotNull Set<BuildEntry> buildEntries, boolean isOutdated) {
    myPath = path;
    myBuildEntries = new HashSet<>(buildEntries);
    myOutdated = isOutdated;
  }

  @NotNull
  String getPath() {
    return myPath;
  }

  @NotNull
  Set<BuildEntry> getBuildEntries() {
    return myBuildEntries;
  }

  boolean isOutdated() {
    return myOutdated;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BuildTypeEntry that = (BuildTypeEntry) o;
    return Objects.equals(myPath, that.myPath);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myPath);
  }

  @Override
  public String toString() {
    return "BuildTypeEntry{" +
      "myPath='" + myPath + '\'' +
      ", myBuildEntries=" + myBuildEntries +
      ", myOutdated=" + myOutdated +
      '}';
  }
}
