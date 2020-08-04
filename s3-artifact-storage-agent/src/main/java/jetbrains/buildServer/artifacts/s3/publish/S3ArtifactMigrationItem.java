package jetbrains.buildServer.artifacts.s3.publish;

import java.io.File;
import java.io.InputStream;
import jetbrains.buildServer.artifacts.migration.ArtifactMigrationItem;
import org.jetbrains.annotations.NotNull;

public class S3ArtifactMigrationItem implements ArtifactMigrationItem {
  @NotNull private final String myPath;
  public S3ArtifactMigrationItem(@NotNull String path) {
    myPath = path;
  }
  //S3 does not have file entity, so method returns null
  @Override
  public File getFile() {
    return null;
  }

  // S3 storage does not have file entity and returns InputStream
  @Override
  public InputStream getInputStream() {
    //TODO
    return null;
  }
}
