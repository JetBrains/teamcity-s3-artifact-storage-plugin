package jetbrains.buildServer.artifacts.s3.transfer;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import jetbrains.buildServer.artifacts.ArtifactStorageSettings;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.transfer.model.*;
import jetbrains.buildServer.artifacts.s3.transfer.storage.LocalStorage;
import jetbrains.buildServer.artifacts.s3.transfer.storage.S3Storage;
import jetbrains.buildServer.artifacts.s3.transfer.storage.Storage;
import org.jetbrains.annotations.NotNull;

public class ProjectProcessor {
  private final TeamCityClient myClient;
  private final ExecutorService myExecutor;

  public ProjectProcessor(TeamCityClient client, ExecutorService executor) {
    myClient = client;
    myExecutor = executor;
  }

  public void process(String projectId, Parameters parameters) throws IOException {
    process(projectId, parameters, Collections.emptyList());
  }

  private void process(String projectId, Parameters parameters, List<Feature> parentFeatures) throws IOException {
    Project project = myClient.getDetails(projectId);

    List<Feature> features = project.getProjectFeatures().getProjectFeature();

    //TODO Deal with duplicate active storage settings if both project and parent project have active storage settings
    String targetType = parameters.getTarget();
    Optional<String> projectActiveStorageId = getActiveStorageFeatureId(features);

    if (!projectActiveStorageId.isPresent()) {
      projectActiveStorageId = getActiveStorageFeatureId(parentFeatures);
    }

    if (!projectActiveStorageId.isPresent()) {
      throw new RuntimeException("No active storage feature found");
    }
    String activeStorageFeatureId = projectActiveStorageId.get();

    Storage targetStorage = getStorage(features, targetType);
    if (!targetStorage.getFeatureId().equals(activeStorageFeatureId)) {
      targetStorage = getStorage(parentFeatures, targetType);
    }

    if (!targetStorage.getFeatureId().equals(projectActiveStorageId.get())) {
      throw new RuntimeException("Active storage is not the same as target storage. Skipping project");
    }
    Storage finalTargetStorage = targetStorage;

    Storage sourceStorage = getStorage(features, parameters.getSource());

    List<Build> builds = myClient.getBuilds(projectId);

    List<BuildArtifacts> artifacts = new ArrayList<>();
    for (Build build : builds) {
      artifacts.add(myClient.getArtifacts(build.getId()));
    }
    for (BuildArtifacts buildInfo : artifacts) {
      myExecutor.submit(() -> {
        processBuild(buildInfo, sourceStorage, finalTargetStorage);
      });
    }

    features.addAll(parentFeatures);
    for (Project subProject : project.getProjects().getProject()) {
      process(subProject.getId(), parameters, features);
    }
  }

  @NotNull
  private Optional<String> getActiveStorageFeatureId(List<Feature> features) {
    return features.stream()
                   .filter(f -> f.getType().equals(ArtifactStorageSettings.ACTIVE_STORAGE_FEATURE_TYPE))
                   .flatMap(f -> f.getProperties().getProperty().stream())
                   .filter(p -> ArtifactStorageSettings.ACTIVE_STORAGE_KEY.equals(p.getName()))
                   .findFirst()
                   .map(Property::getValue);
  }

  private void processBuild(BuildArtifacts buildInfo, Storage sourceStorage, Storage targetStorage) {
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

  @NotNull
  private Storage getStorage(List<Feature> features, String type) {
    if (ArtifactStorageSettings.DEFAULT_TYPE.equals(type)) {
      return getStorage(ArtifactStorageSettings.DEFAULT_TYPE, Collections.emptyMap());
    } else {
      Feature feature = features.stream()
                                .filter(f -> f.getProperties().getProperty().stream()
                                              .anyMatch(p -> p.getName().equals(ArtifactStorageSettings.TEAMCITY_STORAGE_TYPE_KEY) &&
                                                             p.getValue().equals(type)))
                                .findFirst()
                                .orElseThrow(() -> new RuntimeException("Storage " + type + " not found"));

      Map<String, String> storageProperties = feature.getProperties().getProperty().stream()
                                                     .filter(p -> p.getValue() != null)
                                                     .collect(Collectors.toMap(Property::getName, Property::getValue));

      return getStorage(feature.getId(), storageProperties);
    }
  }

  private Storage getStorage(String featureId, Map<String, String> storageProperties) {
    String storageType = storageProperties.getOrDefault(ArtifactStorageSettings.TEAMCITY_STORAGE_TYPE_KEY, featureId);

    switch (storageType) {
      case S3Constants.S3_STORAGE_TYPE:
        return new S3Storage(featureId, storageProperties);
      case ArtifactStorageSettings.DEFAULT_TYPE:
        return new LocalStorage(featureId);
      default:
        throw new RuntimeException("Unknown storage type requested");
    }
  }

}
