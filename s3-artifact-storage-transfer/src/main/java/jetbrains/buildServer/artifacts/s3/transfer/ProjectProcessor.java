package jetbrains.buildServer.artifacts.s3.transfer;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import jetbrains.buildServer.artifacts.ArtifactStorageSettings;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.transfer.model.*;
import jetbrains.buildServer.artifacts.s3.transfer.storage.LocalStorage;
import jetbrains.buildServer.artifacts.s3.transfer.storage.S3Storage;
import jetbrains.buildServer.artifacts.s3.transfer.storage.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ProjectProcessor {
  @NotNull
  private final TeamCityClient myClient;
  @NotNull
  private final ExecutorService myExecutor;

  public ProjectProcessor(@NotNull TeamCityClient client, @NotNull ExecutorService executor) {
    myClient = client;
    myExecutor = executor;
  }

  public void process(@NotNull String projectId, @NotNull Parameters parameters) throws IOException, ExecutionException, InterruptedException {
    process(projectId, parameters, Collections.emptyList());
  }

  private void process(@NotNull String projectId, @NotNull Parameters parameters, @NotNull List<Feature> parentFeatures)
    throws IOException, ExecutionException, InterruptedException {
    Project project = myClient.getDetails(projectId);

    List<Feature> features = project.getProjectFeatures().getProjectFeature();

    Storage targetStorage = getStorage(features, parentFeatures, parameters.getTarget());
    if (targetStorage == null) {
      throw new IllegalArgumentException(String.format("Could not find target storage with name '%s'", parameters.getTarget()));
    }

    Storage sourceStorage = getStorage(features, parentFeatures, parameters.getSource());
    if (sourceStorage == null) {
      throw new IllegalArgumentException(String.format("Could not find source storage with name '%s'", parameters.getSource()));
    }

    List<Build> builds = myClient.getBuilds(projectId);

    List<BuildArtifacts> artifacts = new ArrayList<>();
    for (Build build : builds) {
      artifacts.add(myClient.getArtifacts(build.getId()));
    }
    List<CompletableFuture<Void>> futures = new ArrayList<>();
    for (BuildArtifacts buildInfo : artifacts) {
      futures.add(CompletableFuture.runAsync(() -> {
        processBuild(buildInfo, sourceStorage, targetStorage);
      }, myExecutor));
    }

    //TODO Add timeout settings and configuration
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
    features.addAll(parentFeatures);
    for (Project subProject : project.getProjects().getProject()) {
      process(subProject.getId(), parameters, features);
    }
  }

  @Nullable
  private Storage getStorage(@NotNull List<Feature> features, @NotNull List<Feature> parentFeatures, @NotNull String storageName) {
    if (storageName.equals("default")) {
      return getStorage(ArtifactStorageSettings.DEFAULT_TYPE, Collections.emptyMap());
    }

    Storage storage = getStorage(features, storageName);
    if (storage != null) {
      return storage;
    }

    return getStorage(parentFeatures, storageName);
  }

  private void processBuild(@NotNull BuildArtifacts buildInfo, @NotNull Storage sourceStorage, @NotNull Storage targetStorage) {
    Build metadata = buildInfo.getMetadata();

    List<String> artifacts = buildInfo.getArtifacts();
    for (String artifact : artifacts) {
      File file = sourceStorage.download(artifact, metadata);
      if (file != null) {
        targetStorage.upload(file, metadata);
        sourceStorage.delete(file);
      }
    }
  }

  @Nullable
  private Storage getStorage(@NotNull List<Feature> features, @NotNull String storageName) {
    Optional<Feature> featureOptional = features.stream()
                                                .filter(f -> f.getProperties().getProperty().stream()
                                                              .anyMatch(p -> p.getName().equals(ArtifactStorageSettings.TEAMCITY_STORAGE_NAME_KEY) &&
                                                                             p.getValue().equals(storageName)))
                                                .findFirst();

    if (featureOptional.isPresent()) {
      Feature feature = featureOptional.get();
      Map<String, String> storageProperties = feature.getProperties().getProperty().stream()
                                                     .filter(p -> p.getValue() != null)
                                                     .collect(Collectors.toMap(Property::getName, Property::getValue));

      return getStorage(feature.getId(), storageProperties);
    } else {
      return null;
    }
  }

  @NotNull
  private Storage getStorage(@NotNull String featureId, @NotNull Map<String, String> storageProperties) {
    String storageType = storageProperties.getOrDefault(ArtifactStorageSettings.TEAMCITY_STORAGE_TYPE_KEY, featureId);

    switch (storageType) {
      case S3Constants.S3_STORAGE_TYPE:
        return new S3Storage(featureId, storageProperties);
      case ArtifactStorageSettings.DEFAULT_TYPE:
        return new LocalStorage(featureId);
      default:
        throw new RuntimeException("Unsupported storage type requested");
    }
  }

}
