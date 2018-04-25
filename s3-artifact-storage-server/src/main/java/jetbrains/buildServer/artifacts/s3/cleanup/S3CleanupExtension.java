package jetbrains.buildServer.artifacts.s3.cleanup;

import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.MultiObjectDeleteException;
import com.google.common.collect.Lists;
import jetbrains.buildServer.artifacts.ArtifactData;
import jetbrains.buildServer.artifacts.ArtifactListData;
import jetbrains.buildServer.artifacts.ServerArtifactStorageSettingsProvider;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.artifacts.s3.util.ParamUtil;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.artifacts.ServerArtifactHelper;
import jetbrains.buildServer.serverSide.cleanup.BuildCleanupContext;
import jetbrains.buildServer.serverSide.cleanup.CleanupExtension;
import jetbrains.buildServer.serverSide.cleanup.CleanupPolicy;
import jetbrains.buildServer.serverSide.cleanup.CleanupProcessState;
import jetbrains.buildServer.serverSide.impl.cleanup.HistoryRetentionPolicy;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.positioning.PositionAware;
import jetbrains.buildServer.util.positioning.PositionConstraint;
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
public class S3CleanupExtension implements CleanupExtension, PositionAware {

  @NotNull private final ServerArtifactStorageSettingsProvider mySettingsProvider;
  @NotNull private final ServerArtifactHelper myHelper;
  @NotNull private final ServerPaths myServerPaths;

  public S3CleanupExtension(
    @NotNull ServerArtifactHelper helper,
    @NotNull ServerArtifactStorageSettingsProvider settingsProvider,
    @NotNull ServerPaths serverPaths) {
    myHelper = helper;
    mySettingsProvider = settingsProvider;
    myServerPaths = serverPaths;
  }

  @Override
  public void cleanupBuildsData(@NotNull BuildCleanupContext buildCleanupContext) throws Exception {
    for (SFinishedBuild build : buildCleanupContext.getBuilds()) {
      try {
        final ArtifactListData artifactsInfo = myHelper.getArtifactList(build);
        if (artifactsInfo == null) continue;

        final String pathPrefix = S3Util.getPathPrefix(artifactsInfo);
        if (pathPrefix == null) continue;

        final String patterns = getPatternsForBuild(buildCleanupContext, build);
        final List<String> toDelete = getPathsToDelete(artifactsInfo, patterns, pathPrefix);
        if (toDelete.isEmpty()) continue;

        final Map<String, String> params = S3Util.validateParameters(mySettingsProvider.getStorageSettings(build));

        final String bucketName = S3Util.getBucketName(params);
        S3Util.withS3Client(ParamUtil.putSslValues(myServerPaths, params), client -> {
          final String suffix = " from S3 bucket [" + bucketName + "]" + " from path [" + pathPrefix + "]";

          int succeededNum = 0;
          int errorNum = 0;

          for (List<String> part: Lists.partition(toDelete, 1000)) {
            try {
              final DeleteObjectsRequest deleteObjectsRequest = new DeleteObjectsRequest(bucketName)
                .withKeys(part.stream().map(path -> new DeleteObjectsRequest.KeyVersion(pathPrefix + path)).collect(Collectors.toList()));

              succeededNum += client.deleteObjects(deleteObjectsRequest).getDeletedObjects().size();
            } catch (MultiObjectDeleteException e) {
              succeededNum += e.getDeletedObjects().size();

              final List<MultiObjectDeleteException.DeleteError> errors = e.getErrors();
              errors.forEach(error -> {
                final String key = error.getKey();
                if (key.startsWith(pathPrefix)) {
                  Loggers.CLEANUP.debug("Failed to remove " + key + " from S3 bucket " + bucketName + ": " + error.getMessage());
                  toDelete.remove(key.substring(pathPrefix.length()));
                }
              });
              errorNum += errors.size();
            }
          }

          if (errorNum > 0) {
            buildCleanupContext.getErrorReporter().buildCleanupError(build.getBuildId(), "Failed to remove [" + errorNum + "] s3 " + StringUtil.pluralize("object", errorNum) + suffix);
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
  private String getPatternsForBuild(@NotNull final BuildCleanupContext cleanupContext, @NotNull final SBuild build) {
    if (cleanupContext.getCleanupLevel().isCleanHistoryEntry()) return StringUtil.EMPTY;

    final CleanupPolicy policy = cleanupContext.getCleanupPolicyForBuild(build.getBuildId());
    return StringUtil.emptyIfNull(policy.getParameters().get(HistoryRetentionPolicy.ARTIFACT_PATTERNS_PARAM));
  }

  @NotNull
  @Override
  public String getOrderId() {
    return S3Constants.S3_STORAGE_TYPE;
  }
}
