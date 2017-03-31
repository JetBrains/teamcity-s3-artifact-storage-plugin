package jetbrains.buildServer.artifacts.s3.cleanup;

import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.MultiObjectDeleteException;
import jetbrains.buildServer.artifacts.ArtifactData;
import jetbrains.buildServer.artifacts.ArtifactListData;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.artifacts.ServerArtifactHelper;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by Nikita.Skvortsov
 * date: 08.04.2016.
 */
public class S3CleanupExtension implements CleanupExtension, PositionConstraintAware {

  @NotNull private final ArtifactsStorageSettingsProvider mySettingsProvider;
  @NotNull private final ServerArtifactHelper myHelper;

  public S3CleanupExtension(@NotNull ServerArtifactHelper helper, @NotNull ArtifactsStorageSettingsProvider settingsProvider) {
    myHelper = helper;
    mySettingsProvider = settingsProvider;
  }

  @Override
  public void cleanupBuildsData(@NotNull BuildCleanupContext buildCleanupContext) throws Exception {
    for (SFinishedBuild build : buildCleanupContext.getBuilds()) {
      try {
        final ArtifactListData artifactsInfo = myHelper.getArtifactList(build);
        if (artifactsInfo == null) return;

        final String pathPrefix = S3Util.getPathPrefix(artifactsInfo);
        if (pathPrefix == null) return;

        final String patterns = getPatternsForBuild((BuildCleanupContextEx) buildCleanupContext, build);
        final List<String> toDelete = getPathsToDelete(artifactsInfo, patterns, pathPrefix);
        if (toDelete.isEmpty()) return;

        final Map<String, String> params = S3Util.validateParameters(mySettingsProvider.getStorageSettings(String.valueOf(build.getBuildId())));

        final String bucketName = S3Util.getBucketName(params);
        AWSCommonParams.withAWSClients(params, awsClients -> {
          final String suffix = " from S3 bucket [" + bucketName + "]" + " from path [" + pathPrefix + "]";
          int succeededNum;
          try {
            final DeleteObjectsRequest deleteObjectsRequest =
              new DeleteObjectsRequest(bucketName)
                .withKeys(toDelete.stream().map(path -> new DeleteObjectsRequest.KeyVersion(pathPrefix + path)).collect(Collectors.toList()));
            succeededNum = awsClients.createS3Client().deleteObjects(deleteObjectsRequest).getDeletedObjects().size();
          } catch (MultiObjectDeleteException e) {
            succeededNum = e.getDeletedObjects().size();

            final List<MultiObjectDeleteException.DeleteError> errors = e.getErrors();
            errors.forEach(error -> {
              final String key = error.getKey();
              if (key.startsWith(pathPrefix)) {
                Loggers.CLEANUP.debug("Failed to remove " + key + " from S3 bucket " + bucketName + ": " + error.getMessage());
                toDelete.remove(key.substring(pathPrefix.length()));
              }
            });
            buildCleanupContext.getErrorReporter().buildCleanupError(build.getBuildId(), "Failed to remove [" + errors.size() + "] s3 " + StringUtil.pluralize("object", errors.size()) + suffix);
          }

          Loggers.CLEANUP.info("Removed [" + succeededNum + "] s3 " + StringUtil.pluralize("object", succeededNum) + suffix);

          myHelper.removeFromArtifactList(build, toDelete);

          return null;
        });

      } catch (Throwable e) {
        Loggers.CLEANUP.debug(e);
        buildCleanupContext.getErrorReporter().buildCleanupError(build.getBuildId(), "Failed to remove s3 artifacts: " + e.getMessage());
      }
    }
  }

  @NotNull
  private List<String> getPathsToDelete(@NotNull ArtifactListData artifactsInfo, @NotNull String patterns, @NotNull String pathPrefix) throws IOException {
    final List<String> keys = CollectionsUtil.convertCollection(artifactsInfo.getArtifactList(), ArtifactData::getPath);
    return new ArrayList<>(new PathPatternFilter(patterns).filterPaths(keys));
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
