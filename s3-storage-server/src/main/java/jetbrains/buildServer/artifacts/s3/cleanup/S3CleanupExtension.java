package jetbrains.buildServer.artifacts.s3.cleanup;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.DeleteObjectsResult;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.artifacts.util.ExternalArtifactUtil;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactHolder;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactsViewMode;
import jetbrains.buildServer.serverSide.cleanup.BuildCleanupContext;
import jetbrains.buildServer.serverSide.cleanup.CleanupExtension;
import jetbrains.buildServer.serverSide.cleanup.CleanupProcessState;
import jetbrains.buildServer.serverSide.storage.StorageTypeRegistry;
import jetbrains.buildServer.util.positioning.PositionConstraint;
import jetbrains.buildServer.util.positioning.PositionConstraintAware;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by Nikita.Skvortsov
 * date: 08.04.2016.
 */
public class S3CleanupExtension implements CleanupExtension, PositionConstraintAware {

  @NotNull
  private final StorageTypeRegistry myRegistry;

  public S3CleanupExtension(@NotNull StorageTypeRegistry registry) {
    myRegistry = registry;
  }

  @Override
  public void cleanupBuildsData(@NotNull BuildCleanupContext buildCleanupContext) throws Exception {
    final List<SFinishedBuild> builds = buildCleanupContext.getBuilds();
    for (SFinishedBuild build : builds) {
      final BuildArtifactHolder artifact = build.getArtifacts(BuildArtifactsViewMode.VIEW_ALL).findArtifact(S3Constants.S3_ARTIFACTS_LIST_PATH + "/" + S3Constants.S3_ARTIFACTS_LIST);
      if (artifact.isAvailable()) {
        final SBuildType buildType = build.getBuildType();
        if (buildType == null) {
          Loggers.CLEANUP.warn("Build " + build.getBuildDescription() + " had artifact uploaded to S3 storage, but project is no longer available. Can not find S3 storage configuration. " +
              "Can not remove artifacts from S3");
        } else {
          final SProject project = buildType.getProject();
          final Map<String, String> cfg = myRegistry.getStorageParams(project, S3Constants.S3_STORAGE_TYPE);
          if (cfg == null) {
            Loggers.CLEANUP.warn("Build " + build.getBuildDescription() + " had artifact uploaded to S3 storage, but project " + project.getDescription() + " has no S3 storage configuration. " +
                "Can not remove artifacts from S3");
          } else {
            final AmazonS3 amazonClient = S3Util.createAmazonClient(cfg);

            final String bucketName = cfg.get(S3Constants.S3_BUCKET_NAME);
            final DeleteObjectsResult result = amazonClient.deleteObjects(new DeleteObjectsRequest(bucketName)
                .withKeys(ExternalArtifactUtil.readExternalArtifacts(artifact.getArtifact().getInputStream())
                    .stream()
                    .map(ea -> ea.getProperties().get(S3Constants.S3_KEY))
                    .map(DeleteObjectsRequest.KeyVersion::new)
                    .collect(Collectors.toList())));

            final int size = result.getDeletedObjects().size();
            Loggers.CLEANUP.info("Removed [" + size + "] s3 object" + (size > 1 ? "s" : "") + " from S3 bucket [" + bucketName + "]");
          }
        }
      }
    }
  }

  @Override
  public void afterCleanup(@NotNull CleanupProcessState cleanupProcessState) throws Exception {
    // do nothing
  }

  @NotNull
  @Override
  public PositionConstraint getConstraint() {
    return PositionConstraint.first();
  }
}
