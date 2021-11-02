package jetbrains.buildServer.artifacts.s3.transfer.storage;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import jetbrains.buildServer.ArtifactsConstants;
import jetbrains.buildServer.artifacts.ArtifactData;
import jetbrains.buildServer.artifacts.ArtifactDataInstance;
import jetbrains.buildServer.artifacts.ArtifactListData;
import jetbrains.buildServer.artifacts.s3.transfer.model.Build;
import jetbrains.buildServer.artifacts.s3.transfer.model.Feature;
import jetbrains.buildServer.artifacts.util.ArtifactListUtil;
import jetbrains.buildServer.artifacts.util.SerializableArtifactData;
import jetbrains.buildServer.artifacts.util.SerializableArtifactListData;
import jetbrains.buildServer.serverSide.impl.artifacts.FileAwareBufferedInputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractStorage implements Storage {

  protected void addToArtifactList(@NotNull File artifact, @NotNull Build metadata) {
    SerializableArtifactListData artifactListData = readArtifactList(metadata);

    if (artifactListData == null) {
      artifactListData = new SerializableArtifactListData(getFeatureId(), getCommonProperties(metadata), new ArrayList<>());
    }

    List<ArtifactData> artifactList = new ArrayList<>(artifactListData.getArtifactList());
    Optional<ArtifactData> listedArtifact = artifactList.stream().filter(a -> a.getPath().equals(artifact.getName())).findAny();

    if (!listedArtifact.isPresent()) {
      ArtifactDataInstance artifactData = ArtifactDataInstance.create(artifact.getName(), artifact.length());
      artifactList.add(SerializableArtifactData.toSerializableArtifactData(artifactData));
      artifactListData = new SerializableArtifactListData(artifactListData.getStorageSettingsId(), artifactListData.getCommonProperties(), artifactList);
      writeArtifactList(metadata, artifactListData);
    }

  }

  @NotNull
  protected Map<String, String> getCommonProperties(@NotNull Build metadata) {
    return new HashMap<>();
  }

  protected boolean listedAsArtifact(@NotNull String name, @NotNull Build metadata) {
    ArtifactListData artifactListData = readArtifactList(metadata);
    if (artifactListData == null) {
      return false;
    }
    Optional<ArtifactData> artifact = artifactListData.getArtifactList().stream().filter(a -> a.getPath().equals(name)).findAny();
    return artifact.isPresent();
  }

  @Nullable
  private SerializableArtifactListData readArtifactList(@NotNull Build metadata) {
    String artifactsDirectory = metadata.getDirectory();

    final File artifactListFile = new File(artifactsDirectory, ArtifactsConstants.ARTIFACT_LIST_PATH);

    if (!artifactListFile.isFile()) return null;

    SerializableArtifactListData artifactListData = null;
    try {
      artifactListData = ArtifactListUtil.readArtifactList(new FileAwareBufferedInputStream(artifactListFile));
    } catch (FileNotFoundException e) {
      //TODO Logging
      e.printStackTrace();
    }

    return artifactListData;
  }

  private void writeArtifactList(@NotNull Build metadata, @NotNull SerializableArtifactListData artifactListData) {
    String artifactsDirectory = metadata.getDirectory();

    final File artifactListFile = new File(artifactsDirectory, ArtifactsConstants.ARTIFACT_LIST_PATH);

    ArtifactListUtil.writeArtifactList(artifactListData, artifactListFile);
  }

  @Override
  public void init(Feature feature) {
    //do nothing
  }
}
