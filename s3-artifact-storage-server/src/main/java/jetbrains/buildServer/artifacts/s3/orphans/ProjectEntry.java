package jetbrains.buildServer.artifacts.s3.orphans;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

class ProjectEntry {

  private final String myPath;
  private final Set<BuildTypeEntry> myBuildTypeEntries;
  private final boolean myOutdated;

  ProjectEntry(@NotNull String path, @NotNull Set<BuildTypeEntry> buildTypeEntries) {
    myPath = path;

    myBuildTypeEntries = buildTypeEntries;

    long outdatedCount = buildTypeEntries.stream()
      .filter(BuildTypeEntry::isOutdated)
      .count();

    myOutdated = myBuildTypeEntries.size() == outdatedCount;
  }

  @NotNull
  String getPath() {
    return myPath;
  }

  @NotNull
  Set<BuildTypeEntry> getBuildTypeEntries() {
    return myBuildTypeEntries;
  }

  boolean isOutdated() {
    return myOutdated;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ProjectEntry that = (ProjectEntry) o;
    return Objects.equals(myPath, that.myPath);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myPath);
  }

  @Override
  public String toString() {
    return "ProjectEntry{" +
      "myPath='" + myPath + '\'' +
      ", myBuildTypeEntries=" + myBuildTypeEntries +
      ", myOutdated=" + myOutdated +
      '}';
  }
}
