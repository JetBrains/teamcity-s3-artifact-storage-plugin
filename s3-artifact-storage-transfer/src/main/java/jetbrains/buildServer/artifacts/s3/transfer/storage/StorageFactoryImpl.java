package jetbrains.buildServer.artifacts.s3.transfer.storage;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import jetbrains.buildServer.artifacts.ArtifactStorageSettings;
import jetbrains.buildServer.artifacts.s3.transfer.model.Feature;
import org.jetbrains.annotations.NotNull;

public class StorageFactoryImpl {

  private final Map<String, Storage> myStorages;

  public StorageFactoryImpl(@NotNull Collection<Storage> storages) {
    myStorages = storages.stream().collect(Collectors.toMap(s -> s.getType(), s -> s));
  }

  @NotNull
  public Storage getStorage(@NotNull Feature feature) {
    String featureId = feature.getId();
    String storageType = feature.getProperties().getOrDefault(ArtifactStorageSettings.TEAMCITY_STORAGE_TYPE_KEY, featureId);

    Storage storage = myStorages.get(storageType);

    if (storage == null) {
      throw new RuntimeException("Unsupported storage type requested");
    }

    storage.init(feature);

    return storage;
  }

  public Storage getDefaultStorage() {
    return new LocalStorage();
  }
}
