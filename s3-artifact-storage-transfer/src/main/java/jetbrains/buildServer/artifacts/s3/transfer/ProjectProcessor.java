package jetbrains.buildServer.artifacts.s3.transfer;

import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import jetbrains.buildServer.artifacts.ArtifactStorageSettings;
import jetbrains.buildServer.artifacts.s3.transfer.model.Build;
import jetbrains.buildServer.artifacts.s3.transfer.model.Feature;
import jetbrains.buildServer.artifacts.s3.transfer.model.Project;
import jetbrains.buildServer.artifacts.s3.transfer.storage.Storage;
import jetbrains.buildServer.artifacts.s3.transfer.storage.StorageFactoryImpl;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.artifacts.s3.transfer.settings.ArtifactTransferConstants.BUILD_PROCESSING_TIMEOUT;
import static jetbrains.buildServer.artifacts.s3.transfer.settings.ArtifactTransferConstants.DEFAULT_BUILD_PROCESSING_TIMEOUT;

public class ProjectProcessor {
  private final static Logger LOG = Logger.getInstance(ProjectProcessor.class.getName());

  @NotNull
  private final TeamCityClient myClient;
  private StorageFactoryImpl myStorageFactory;
  @NotNull
  private final ExecutorService myExecutor;

  public ProjectProcessor(@NotNull TeamCityClient client, @NotNull StorageFactoryImpl storageFactory, @NotNull ExecutorService executor) {
    myClient = client;
    myStorageFactory = storageFactory;
    myExecutor = executor;
  }

  public void process(@NotNull String projectId, @NotNull String source, @NotNull String target) throws IOException, ExecutionException, InterruptedException, TimeoutException {
    process(projectId, source, target, Collections.emptyList());
  }

  private void process(@NotNull String projectId, @NotNull String source, @NotNull String target, Collection<Feature> parentFeatures)
    throws IOException, ExecutionException, InterruptedException, TimeoutException {
    Project project = myClient.getDetails(projectId);

    Collection<Feature> features = project.getFeatures();

    Storage targetStorage = getStorage(features, parentFeatures, target);
    if (targetStorage == null) {
      String message = String.format("Could not find target storage with name '%s'", target);
      LOG.error(message);
      throw new IllegalArgumentException(message);
    }

    Storage sourceStorage = getStorage(features, parentFeatures, source);
    if (sourceStorage == null) {
      String message = String.format("Could not find source storage with name '%s'", source);
      LOG.error(message);
      throw new IllegalArgumentException(message);
    }

    List<String> builds = myClient.getBuilds(projectId);

    List<CompletableFuture<Void>> futures = new ArrayList<>();
    for (String buildId : builds) {
      Build build = myClient.getArtifacts(buildId);
      futures.add(CompletableFuture.runAsync(() -> {
        processBuild(build, sourceStorage, targetStorage);
      }, myExecutor));
    }

    int timeout = TeamCityProperties.getInteger(BUILD_PROCESSING_TIMEOUT, DEFAULT_BUILD_PROCESSING_TIMEOUT);
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(timeout, TimeUnit.SECONDS);
    features.addAll(parentFeatures);
    for (String subProjectId : project.getSubprojects()) {
      process(subProjectId, source, target, features);
    }
  }

  @Nullable
  private Storage getStorage(@NotNull Collection<Feature> features, @NotNull Collection<Feature> parentFeatures, @NotNull String storageName) {
    if (storageName.equals(ArtifactStorageSettings.DEFAULT_TYPE)) {
      return myStorageFactory.getDefaultStorage();
    }

    Storage storage = getStorage(features, storageName);
    if (storage != null) {
      return storage;
    }

    return getStorage(parentFeatures, storageName);
  }

  private void processBuild(@NotNull Build buildInfo, @NotNull Storage sourceStorage, @NotNull Storage targetStorage) {
    List<String> artifacts = buildInfo.getArtifacts();
    for (String artifact : artifacts) {
      File file = sourceStorage.download(artifact, buildInfo);
      if (file != null) {
        targetStorage.upload(file, buildInfo);
        sourceStorage.delete(file);
      } else {
        LOG.warn(String.format("Artifact '%s' for build '%s' not found in storage '%s'", artifact, buildInfo.getId(), sourceStorage.getType()));
      }
    }
  }

  @Nullable
  private Storage getStorage(@NotNull Collection<Feature> features, @NotNull String storageName) {
    return features.stream()
                   .filter(f -> f.getType().equals(ArtifactStorageSettings.STORAGE_FEATURE_TYPE))
                   .filter(f -> storageName.equals(f.getProperties().get(ArtifactStorageSettings.TEAMCITY_STORAGE_NAME_KEY)))
                   .map(f -> myStorageFactory.getStorage(f))
                   .findFirst()
                   .orElse(null);
  }



}
