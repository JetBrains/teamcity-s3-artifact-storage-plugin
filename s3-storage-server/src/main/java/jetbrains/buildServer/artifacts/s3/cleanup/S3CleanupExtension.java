package jetbrains.buildServer.artifacts.s3.cleanup;

import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import jetbrains.buildServer.artifacts.ExternalArtifact;
import jetbrains.buildServer.artifacts.ExternalArtifactsInfo;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.artifacts.util.ServerExternalArtifactUtil;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.cleanup.BuildCleanupContext;
import jetbrains.buildServer.serverSide.cleanup.BuildCleanupContextEx;
import jetbrains.buildServer.serverSide.cleanup.CleanupExtension;
import jetbrains.buildServer.serverSide.cleanup.CleanupProcessState;
import jetbrains.buildServer.serverSide.impl.cleanup.HistoryRetentionPolicy;
import jetbrains.buildServer.storage.ArtifactsStorageSettingsProvider;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import jetbrains.buildServer.util.positioning.PositionConstraint;
import jetbrains.buildServer.util.positioning.PositionConstraintAware;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Created by Nikita.Skvortsov
 * date: 08.04.2016.
 */
public class S3CleanupExtension implements CleanupExtension, PositionConstraintAware {

  @NotNull private final ArtifactsStorageSettingsProvider mySettingsProvider;

  public S3CleanupExtension(@NotNull ArtifactsStorageSettingsProvider settingsProvider) {
    mySettingsProvider = settingsProvider;
  }

  @Override
  public void cleanupBuildsData(@NotNull BuildCleanupContext buildCleanupContext) throws Exception {
    for (SFinishedBuild build : buildCleanupContext.getBuilds()) {

      final ExternalArtifactsInfo artifactsInfo = ServerExternalArtifactUtil.getExternalArtifactsInfo(build);
      if (artifactsInfo == null) return;

      final String pathPrefix = S3Util.getPathPrefix(artifactsInfo);
      if (pathPrefix == null) return;

      final Map<String, String> params = S3Util.validateParameters(mySettingsProvider.getStorageSettings(String.valueOf(build.getBuildId())));

      final String patterns = getPatternsForBuild((BuildCleanupContextEx) buildCleanupContext, build);
      final List<DeleteObjectsRequest.KeyVersion> toDelete = getObjectsToDelete(artifactsInfo, patterns, pathPrefix);
      if (toDelete.isEmpty()) return;

      final String bucketName = S3Util.getBucketName(params);
      AWSCommonParams.withAWSClients(params, awsClients -> {
        final DeleteObjectsRequest deleteObjectsRequest = new DeleteObjectsRequest(bucketName).withKeys(toDelete);
        final int size = awsClients.createS3Client().deleteObjects(deleteObjectsRequest).getDeletedObjects().size();

        Loggers.CLEANUP.info("Removed [" + size + "] s3 " + StringUtil.pluralize("object", size) + " from S3 bucket [" + bucketName + "]");
        return null;
      });
    }
  }

  @NotNull
  private List<DeleteObjectsRequest.KeyVersion> getObjectsToDelete(@NotNull ExternalArtifactsInfo artifactsInfo, @NotNull String patterns, @NotNull String pathPrefix) throws IOException {
    final List<String> keys = CollectionsUtil.convertCollection(artifactsInfo.getExternalArtifactList(), ExternalArtifact::getPath);
    return CollectionsUtil.convertCollection(new PathPatternFilter(patterns).filterPaths(keys), source -> new DeleteObjectsRequest.KeyVersion(pathPrefix + source));
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
  private String getPatternsForBuild(@NotNull final BuildCleanupContextEx cleanupContext, @NotNull final SBuild build) {
    final HistoryRetentionPolicy policy = cleanupContext.getCleanupPolicyForBuild(build.getBuildId());
    return StringUtil.emptyIfNull(policy.getParameters().get(HistoryRetentionPolicy.ARTIFACT_PATTERNS_PARAM));
  }
}
