package jetbrains.buildServer.artifacts.s3.migration;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Consumer;
import jetbrains.buildServer.artifacts.ArtifactDataInstance;
import jetbrains.buildServer.artifacts.migration.ArtifactMigrationItem;
import jetbrains.buildServer.artifacts.migration.BuildArtifactUploader;
import jetbrains.buildServer.artifacts.migration.BuildSharedConfigurationProperties;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.S3UploadingHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class S3MigrationUploader implements BuildArtifactUploader {
  @NotNull
  private static final Logger LOG = Logger.getInstance(S3MigrationUploader.class.getName());

  @Override
  public Collection<ArtifactDataInstance> uploadFile(@NotNull final Map<String, String> buildConfigParametersForMigration,
                                                     @NotNull final Map<String, String> buildArtifactStorageSettings,
                                                     @NotNull final ArtifactMigrationItem item,
                                                     @NotNull final String pathFileToUpload) throws FileNotFoundException {
    final HashMap<File, String> filesToPublish = new HashMap<>();

    // Handling different types of artifacts, which were downloaded from storage
    File fileToUpload;
    if (item.getFile() == null) {
      try {
        fileToUpload = writeFile(item.getInputStream(), pathFileToUpload);
      } catch (FileNotFoundException e) {
        LOG.error("Failed to get input stream from artifact.", e);
        throw e;
      }
    } else {
      fileToUpload = item.getFile();
    }

    filesToPublish.put(fileToUpload, pathFileToUpload);

    final int numberOfRetries = Integer.parseInt(buildConfigParametersForMigration.getOrDefault(BuildSharedConfigurationProperties.NUMBER_OF_RETRIES,
                                                                               String.valueOf(S3Constants.DEFAULT_S3_NUMBER_OF_RETRIES_ON_ERROR)));
    final int retryDelay = Integer.parseInt(buildConfigParametersForMigration.getOrDefault(BuildSharedConfigurationProperties.RETRY_DELAY,
                                                                                           String.valueOf(S3Constants.DEFAULT_S3_RETRY_DELAY_ON_ERROR_MS)));

    String pathPrefix = getPathPrefix(buildConfigParametersForMigration, buildArtifactStorageSettings).second;

    return uploadFiles(pathPrefix, filesToPublish, "", numberOfRetries, retryDelay, buildArtifactStorageSettings, LOG::error);
  }

  public Collection<ArtifactDataInstance> uploadFiles(@NotNull final String pathPrefix,
                                                      @NotNull final Map<File, String> filesToPublish,
                                                      @Nullable final String certDirectory,
                                                      final int numberOfRetries,
                                                      final int retryDelay,
                                                      final Map<String, String> artifactStorageSettings,
                                                      @Nullable final Consumer<String> errorListener) {
    List<ArtifactDataInstance> artifactDataInstances = new ArrayList<>();
    artifactDataInstances.addAll(S3UploadingHelper.publishFiles(pathPrefix, filesToPublish, certDirectory,
                                                                numberOfRetries, retryDelay, artifactStorageSettings, errorListener));
    return artifactDataInstances;
  }

  private File writeFile(@NotNull final InputStream inputStream,
                         @NotNull final String pathFileToUpload) {
    Path outputPath = Paths.get(pathFileToUpload);
    try {
      File file = new File(String.valueOf(outputPath));
      file.mkdirs();
      Files.copy(inputStream, outputPath, StandardCopyOption.REPLACE_EXISTING);
      return file;
    } catch (IOException e) {
      LOG.error("Failed to write artifact from storage to file.", e);
      return null;
    }
  }

  @Override
  public Pair<String, String> getPathPrefix(@NotNull final Map<String, String> buildConfigParametersForMigration,
                                            @NotNull final Map<String, String> buildArtifactStorageSettings) {
    return S3UploadingHelper.getPathPrefix(buildConfigParametersForMigration, buildArtifactStorageSettings);
  }

  @Override
  public String getType() {
    return S3Constants.S3_STORAGE_TYPE;
  }
}
