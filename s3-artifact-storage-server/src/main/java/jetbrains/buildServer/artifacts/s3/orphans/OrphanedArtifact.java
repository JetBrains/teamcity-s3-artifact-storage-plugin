package jetbrains.buildServer.artifacts.s3.orphans;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class OrphanedArtifact implements Comparable<OrphanedArtifact>{
    private final String bucket;
    private final String path;
    private final String size;

    public OrphanedArtifact(@NotNull String bucket, @NotNull String path, @Nullable String size) {
        this.bucket = bucket;
        this.path = path;
        this.size = size;
    }

    public String getPath() {
        return path;
    }

    public String getSize() {
        return size;
    }

    public String getBucket() {
        return bucket;
    }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    OrphanedArtifact that = (OrphanedArtifact) o;
    return Objects.equals(bucket, that.bucket) && Objects.equals(path, that.path) && Objects.equals(size, that.size);
  }

  @Override
  public int hashCode() {
    return Objects.hash(bucket, path, size);
  }

  @Override
  public String toString() {
    return "OrphanedArtifact{" +
      "'s3://" + bucket +
      "/" + path +
      "' (" + size + ")}";
  }

  @Override
  public int compareTo(@NotNull OrphanedArtifact o) {
    return (this.bucket + this.path).compareTo(o.bucket + o.path);
  }
}
