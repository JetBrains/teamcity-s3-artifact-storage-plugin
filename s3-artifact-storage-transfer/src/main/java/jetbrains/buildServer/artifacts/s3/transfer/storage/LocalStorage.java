package jetbrains.buildServer.artifacts.s3.transfer.storage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import jetbrains.buildServer.artifacts.ArtifactStorageSettings;
import jetbrains.buildServer.artifacts.s3.transfer.model.Build;
import org.jetbrains.annotations.Nullable;

public class LocalStorage implements Storage {
  private final String myFeatureId;

  public LocalStorage(String featureId) {
    myFeatureId = featureId;
  }

  @Override
  public String getType() {
    return ArtifactStorageSettings.DEFAULT_TYPE;
  }

  @Override
  @Nullable
  public File download(String artifact, Build Build) {
    File file = Paths.get(Build.getDirectory(), artifact).toFile();
    return file.exists() ? file : null;
  }

  @Override
  public void upload(File artifact, Build metadata) {
    try {
      Files.copy(artifact.toPath(), Paths.get(metadata.getDirectory()), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void delete(File artifact) {
    try {
      Files.delete(artifact.toPath());
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public String getFeatureId() {
    return myFeatureId;
  }
}
