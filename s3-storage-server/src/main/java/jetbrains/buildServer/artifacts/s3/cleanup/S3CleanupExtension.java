package jetbrains.buildServer.artifacts.s3.cleanup;

import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import jetbrains.buildServer.artifacts.ExternalArtifact;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.util.ExternalArtifactUtil;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactHolder;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactsViewMode;
import jetbrains.buildServer.serverSide.cleanup.BuildCleanupContext;
import jetbrains.buildServer.serverSide.cleanup.CleanupExtension;
import jetbrains.buildServer.serverSide.cleanup.CleanupProcessState;
import jetbrains.buildServer.serverSide.impl.cleanup.HistoryRetentionPolicy;
import jetbrains.buildServer.serverSide.storage.StorageTypeRegistry;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import jetbrains.buildServer.util.positioning.PositionConstraint;
import jetbrains.buildServer.util.positioning.PositionConstraintAware;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
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
    for (SFinishedBuild build : buildCleanupContext.getBuilds()) {
      final BuildArtifactHolder artifact = build.getArtifacts(BuildArtifactsViewMode.VIEW_HIDDEN_ONLY).findArtifact(S3Constants.EXTERNAL_ARTIFACTS_LIST);
      if (artifact.isAvailable()) {
        final SBuildType buildType = build.getBuildType();
        if (buildType == null) {
          Loggers.CLEANUP.warn("Build " + build.getBuildDescription() +
            " uploaded artifacts to S3 storage, but project is no longer available. Can not find S3 storage configuration. Can not remove artifacts from S3");
          return;
        }

        final SProject project = buildType.getProject();
        final Map<String, String> cfg = myRegistry.getStorageParams(project, S3Constants.S3_STORAGE_TYPE);
        if (cfg == null) {
          Loggers.CLEANUP.warn("Build " + build.getBuildDescription() + " uploaded artifacts to S3 storage, but project " + project.getDescription() +
            " has no S3 storage configuration. Can not remove artifacts from S3");
          return;
        }

        final String bucketName = cfg.get(S3Constants.S3_BUCKET_NAME);
        if (StringUtil.isEmptyOrSpaces(bucketName)) {
          Loggers.CLEANUP.warn("Build " + build.getBuildDescription() + " uploaded artifacts to S3 storage, but project's " + project.getDescription() +
            " S3 storage configuration provides no " + S3Constants.S3_BUCKET_NAME + " parameter. Can not remove artifacts from S3");
          return;
        }

        final String pathPrefix = getPathPrefixProperty(build);
        if (StringUtil.isEmptyOrSpaces(pathPrefix)) {
          Loggers.CLEANUP.warn("Build " + build.getBuildDescription() +
            " uploaded artifacts to S3 storage, but has no " + S3Constants.S3_PATH_PREFIX_SYSTEM_PROPERTY + " system property. Can not remove artifacts from S3");
          return;
        }

        final String patterns = getPatternsForBuild(buildCleanupContext, build);
        final List<DeleteObjectsRequest.KeyVersion> toDelete = getObjectsToDelete(artifact, patterns, pathPrefix);

        if (toDelete.isEmpty()) return;

        AWSCommonParams.withAWSClients(cfg, awsClients -> {
          final DeleteObjectsRequest deleteObjectsRequest = new DeleteObjectsRequest(bucketName).withKeys(toDelete);
          final int size = awsClients.createS3Client().deleteObjects(deleteObjectsRequest).getDeletedObjects().size();

          Loggers.CLEANUP.info("Removed [" + size + "] s3 " + StringUtil.pluralize("object", size) + " from S3 bucket [" + bucketName + "]");
          return null;
        });
      }
    }
  }

  private String getPathPrefixProperty(@NotNull SFinishedBuild build) {
    return build.getParametersProvider().getAll().get("system." + S3Constants.S3_PATH_PREFIX_SYSTEM_PROPERTY);
  }

  @NotNull
  private List<DeleteObjectsRequest.KeyVersion> getObjectsToDelete(@NotNull BuildArtifactHolder artifact, @NotNull String patterns, @NotNull String pathPrefix) throws IOException {
    final List<String> keys = ExternalArtifactUtil.readExternalArtifacts(artifact.getArtifact().getInputStream())
      .stream()
      .map(ExternalArtifact::getPath)
      .collect(Collectors.toList());
    return CollectionsUtil.convertCollection(new PathPatternFilter(patterns).filterPaths(keys), source -> new DeleteObjectsRequest.KeyVersion(pathPrefix + "/" + source));
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

  @NotNull
  private String getPatternsForBuild(@NotNull final BuildCleanupContext cleanupContext, @NotNull final SBuild build) {
    final HistoryRetentionPolicy policy = cleanupContext.getCleanupPolicyForBuild(build.getBuildId());
    return StringUtil.emptyIfNull(policy.getParameters().get(HistoryRetentionPolicy.ARTIFACT_PATTERNS_PARAM));
  }
}
