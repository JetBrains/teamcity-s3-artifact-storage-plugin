package jetbrains.buildServer.artifacts.s3.transfer.storage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import jetbrains.buildServer.artifacts.ArtifactStorageSettings;
import jetbrains.buildServer.artifacts.s3.transfer.model.Build;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LocalStorage implements Storage {
  @NotNull
  private final String myFeatureId;

  public LocalStorage(@NotNull final String featureId) {
    myFeatureId = featureId;
  }

  @NotNull
  @Override
  public String getType() {
    return ArtifactStorageSettings.DEFAULT_TYPE;
  }

  @Nullable
  @Override
  public File download(@NotNull final String artifact, @NotNull final Build metadata) {
    File file = Paths.get(metadata.getDirectory(), artifact).toFile();
    return file.exists() ? file : null;
  }

  @Override
  public void upload(@NotNull final File artifact, @NotNull final Build metadata) {
    try {
      Files.copy(artifact.toPath(), Paths.get(metadata.getDirectory()), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void delete(@NotNull final File artifact) {
    try {
      Files.delete(artifact.toPath());
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  @NotNull
  public String getFeatureId() {
    return myFeatureId;
  }
}
