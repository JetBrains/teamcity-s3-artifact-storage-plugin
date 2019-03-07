package jetbrains.buildServer.artifacts.s3.cleanup;

import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.MultiObjectDeleteException;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.artifacts.ServerArtifactHelper;
import jetbrains.buildServer.serverSide.cleanup.*;
import jetbrains.buildServer.serverSide.impl.cleanup.HistoryRetentionPolicy;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.positioning.PositionAware;
import jetbrains.buildServer.util.positioning.PositionConstraint;
import org.jetbrains.annotations.NotNull;

/**
 * Created by Nikita.Skvortsov
 * date: 08.04.2016.
 */
public class S3CleanupExtension implements CleanupExtension, PositionAware {

  @NotNull
  private final ServerArtifactStorageSettingsProvider mySettingsProvider;
  @NotNull
  private final ServerArtifactHelper myHelper;
  @NotNull
  private final ServerPaths myServerPaths;

  public S3CleanupExtension(
    @NotNull ServerArtifactHelper helper,
    @NotNull ServerArtifactStorageSettingsProvider settingsProvider,
    @NotNull ServerPaths serverPaths) {
    myHelper = helper;
    mySettingsProvider = settingsProvider;
    myServerPaths = serverPaths;
  }

  @Override
  public void cleanupBuildsData(@NotNull BuildCleanupContext buildCleanupContext) {
    for (SFinishedBuild build : buildCleanupContext.getBuilds()) {
      try {
        final ArtifactListData artifactsInfo = myHelper.getArtifactList(build);
        if (artifactsInfo == null) {
          continue;
        }
        final String pathPrefix = S3Util.getPathPrefix(artifactsInfo);
        if (pathPrefix == null) {
          continue;
        }
        final String patterns = getPatternsForBuild(buildCleanupContext, build);
        final List<String> pathsToDelete = getPathsToDelete(artifactsInfo, patterns);
        if (pathsToDelete.isEmpty()) {
          continue;
        }
        doClean(buildCleanupContext.getErrorReporter(), build, pathPrefix, pathsToDelete);
      } catch (Throwable e) {
        Loggers.CLEANUP.debug(e);
        buildCleanupContext.getErrorReporter().buildCleanupError(build.getBuildId(), "Failed to remove S3 artifacts: " + e.getMessage());
      }
    }
  }

  private void doClean(@NotNull ErrorReporter errorReporter, @NotNull SFinishedBuild build, @NotNull String pathPrefix, @NotNull List<String> pathsToDelete)
    throws IOException {
    final Map<String, String> params = S3Util.validateParameters(mySettingsProvider.getStorageSettings(build));
    final String bucketName = S3Util.getBucketName(params);
    S3Util.withS3Client(ParamUtil.putSslValues(myServerPaths, params), client -> {
      final String suffix = " from S3 bucket [" + bucketName + "]" + " from path [" + pathPrefix + "]";

      final AtomicInteger succeededNum = new AtomicInteger();
      final AtomicInteger errorNum = new AtomicInteger();

      final int batchSize = TeamCityProperties.getInteger(S3Constants.S3_CLEANUP_BATCH_SIZE, 1000);
      final boolean useParallelStream = TeamCityProperties.getBooleanOrTrue(S3Constants.S3_CLEANUP_USE_PARALLEL);
      final List<List<String>> partitions = Lists.partition(pathsToDelete, batchSize);
      final Stream<List<String>> listStream = partitions.size() > 1 && useParallelStream
        ? partitions.parallelStream()
        : partitions.stream();

      listStream.forEach(part -> {
        try {
          final DeleteObjectsRequest deleteObjectsRequest = new DeleteObjectsRequest(bucketName)
            .withKeys(part.stream().map(path -> new DeleteObjectsRequest.KeyVersion(pathPrefix + path)).collect(Collectors.toList()));

          succeededNum.addAndGet(client.deleteObjects(deleteObjectsRequest).getDeletedObjects().size());
        } catch (MultiObjectDeleteException e) {
          succeededNum.addAndGet(e.getDeletedObjects().size());

          final List<MultiObjectDeleteException.DeleteError> errors = e.getErrors();
          errors.forEach(error -> {
            final String key = error.getKey();
            if (key.startsWith(pathPrefix)) {
              Loggers.CLEANUP.debug("Failed to remove " + key + " from S3 bucket " + bucketName + ": " + error.getMessage());
              pathsToDelete.remove(key.substring(pathPrefix.length()));
            }
          });
          errorNum.addAndGet(errors.size());
        }
      });

      if (errorNum.get() > 0) {
        String errorMessage = "Failed to remove [" + errorNum + "] S3 " + StringUtil.pluralize("object", errorNum.get()) + suffix;
        errorReporter.buildCleanupError(build.getBuildId(), errorMessage);
      }

      Loggers.CLEANUP.info("Removed [" + succeededNum + "] S3 " + StringUtil.pluralize("object", succeededNum.get()) + suffix);

      myHelper.removeFromArtifactList(build, pathsToDelete);

      return null;
    });
  }

  @NotNull
  private List<String> getPathsToDelete(@NotNull ArtifactListData artifactsInfo, @NotNull String cleanPatterns) {
    final List<String> paths = CollectionsUtil.convertCollection(artifactsInfo.getArtifactList(), ArtifactData::getPath);
    return new ArrayList<>(new PathPatternFilter(cleanPatterns).filterPaths(paths));
  }

  // TW-8717 - new cleanup
  @NotNull
  private List<String> getPathsToDelete(@NotNull ArtifactListData artifactsInfo, @NotNull Collection<String> keepPatterns) {
    List<String> paths = artifactsInfo.getArtifactList().stream().map(ArtifactData::getPath).collect(Collectors.toList());
    Set<String> pathsToKeep = keepPatterns
      .stream()
      .map(keepPattern -> new PathPatternFilter(keepPattern).filterPaths(paths))
      .flatMap(Collection::stream)
      .collect(Collectors.toSet());
    paths.removeAll(pathsToKeep);
    return paths;
  }

  @Override
  public void afterCleanup(@NotNull CleanupProcessState cleanupProcessState) {
    // do nothing
  }

  @NotNull
  @Override
  public PositionConstraint getConstraint() {
    return PositionConstraint.first();
  }

  @NotNull
  private String getPatternsForBuild(@NotNull final BuildCleanupContext cleanupContext, @NotNull final SBuild build) {
    if (cleanupContext.getCleanupLevel().isCleanHistoryEntry()) {
      return StringUtil.EMPTY;
    }
    final CleanupPolicy policy = cleanupContext.getCleanupPolicyForBuild(build.getBuildId());
    return StringUtil.emptyIfNull(policy.getParameters().get(HistoryRetentionPolicy.ARTIFACT_PATTERNS_PARAM));
  }

  @NotNull
  @Override
  public String getOrderId() {
    return S3Constants.S3_STORAGE_TYPE;
  }
}
