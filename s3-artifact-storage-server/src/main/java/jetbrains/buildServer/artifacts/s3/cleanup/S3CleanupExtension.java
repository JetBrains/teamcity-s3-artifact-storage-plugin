

package jetbrains.buildServer.artifacts.s3.cleanup;

import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.DeleteObjectsResult;
import com.amazonaws.services.s3.model.MultiObjectDeleteException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import jetbrains.buildServer.artifacts.ArtifactListData;
import jetbrains.buildServer.artifacts.ServerArtifactStorageSettingsProvider;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.artifacts.s3.amazonClient.AmazonS3Provider;
import jetbrains.buildServer.artifacts.s3.exceptions.InvalidSettingsException;
import jetbrains.buildServer.artifacts.s3.util.ParamUtil;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.artifacts.ServerArtifactHelper;
import jetbrains.buildServer.serverSide.cleanup.BuildCleanupContext;
import jetbrains.buildServer.serverSide.cleanup.BuildCleanupContextEx;
import jetbrains.buildServer.serverSide.cleanup.BuildsCleanupExtension;
import jetbrains.buildServer.serverSide.cleanup.CleanupInterruptedException;
import jetbrains.buildServer.serverSide.connections.credentials.ConnectionCredentialsException;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.serverSide.impl.cleanup.ArtifactPathsEvaluator;
import jetbrains.buildServer.util.NamedThreadFactory;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.Util;
import jetbrains.buildServer.util.amazon.retry.AbstractRetrierEventListener;
import jetbrains.buildServer.util.amazon.retry.Retrier;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.log.Loggers.CLEANUP;

public class S3CleanupExtension implements BuildsCleanupExtension {

  private static final String CONCURRENCY_LEVEL_PROPERTY = "teamcity.cleanup.extensions.s3CleanupExtension.concurrencyLevel";
  private static final String EXCEPTION_MESSAGE = "Got an exception while removing artifacts: ";

  @NotNull
  private final ServerArtifactStorageSettingsProvider mySettingsProvider;
  @NotNull
  private final ServerArtifactHelper myHelper;
  @NotNull
  private final ServerPaths myServerPaths;
  private final ProjectManager myProjectManager;
  private final AmazonS3Provider myAmazonS3Provider;
  @NotNull
  private final List<CleanupListener> myCleanupListeners = new CopyOnWriteArrayList<>(); // is filled from tests only
  private final Map<Long, BuildStorageInfo> myBuildStorageInfos = new ConcurrentHashMap<>();

  public S3CleanupExtension(
    @NotNull ServerArtifactHelper helper,
    @NotNull ServerArtifactStorageSettingsProvider settingsProvider,
    @NotNull ServerPaths serverPaths,
    @NotNull ProjectManager projectManager,
    @NotNull AmazonS3Provider amazonS3Provider) {
    myHelper = helper;
    mySettingsProvider = settingsProvider;
    myServerPaths = serverPaths;
    myProjectManager = projectManager;
    myAmazonS3Provider = amazonS3Provider;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "S3 artifacts cleaner";
  }

  @Override
  public void prepareBuildsData(@NotNull BuildCleanupContext cleanupContext) {
    if (!myBuildStorageInfos.isEmpty()) {
      for (BuildStorageInfo buildStorageInfo : new ArrayList<>(myBuildStorageInfos.values())) {
        CLEANUP.warn("Unexpected S3 artifacts metadadata for " + LogUtil.describe(buildStorageInfo.myBuild) + ". S3 artifacts for this build might not have been fully cleaned.");
      }
      myBuildStorageInfos.clear();
    }
    for (SFinishedBuild build : cleanupContext.getBuilds()) {
      try {
        ArtifactListData artifactsInfo = myHelper.getArtifactList(build);
        if (artifactsInfo == null) {
          continue;
        }
        String pathPrefix = S3Util.getPathPrefix(artifactsInfo);
        if (pathPrefix == null) {
          continue;
        }
        List<String> pathsToDelete = ArtifactPathsEvaluator.getPathsToDelete((BuildCleanupContextEx)cleanupContext, build, artifactsInfo);
        if (pathsToDelete.isEmpty()) {
          continue;
        }
        Map<String, String> storageSettings = mySettingsProvider.getStorageSettings(build);
        Map<String, String> invalids = S3Util.validateParameters(storageSettings, false);
        if (!invalids.isEmpty()) {
          CLEANUP.warn("Failed to remove S3 artifacts in build " + LogUtil.describe(build) + ":\n" + StringUtil.join("\n", invalids.values()));
          cleanupContext.onBuildCleanupError(this, build, "Failed to remove S3 artifacts due to incorrect storage settings configuration.");
          continue;
        }
        myBuildStorageInfos.put(build.getBuildId(), new BuildStorageInfo(build, pathPrefix, pathsToDelete, storageSettings));
      } catch (IOException e) {
        CLEANUP.warn("Failed to get S3 artifacts list in build " + LogUtil.describe(build) + ": " + e.getMessage());
        cleanupContext.onBuildCleanupError(this, build, "Failed to get S3 artifacts list due to IO error.");
      } catch (RuntimeException e) {
        CLEANUP.warn("Failed to remove S3 artifacts " + LogUtil.describe(build) + ": " + e.getMessage());
        cleanupContext.onBuildCleanupError(this, build, "Failed to remove S3 artifacts due to unexpected error.");
      }
    }
  }

  @Override
  public void cleanupBuildsData(@NotNull BuildCleanupContext cleanupContext) throws CleanupInterruptedException {
    for (long buildId : cleanupContext.getBuildIds()) {
      BuildStorageInfo buildStorageInfo = myBuildStorageInfos.remove(buildId);
      if (buildStorageInfo != null) {
        cleanupContext.getCleanupState().throwIfInterrupted();
        SFinishedBuild build = buildStorageInfo.myBuild;
        try {
          doClean(cleanupContext, build, buildStorageInfo.myPathPrefix, buildStorageInfo.myPathsToDelete, buildStorageInfo.myStorageSettings);
        } catch (IOException e) {
          CLEANUP.warn("Failed to remove S3 artifacts: " + e.getMessage());
          cleanupContext.onBuildCleanupError(this, build, "Failed to remove S3 artifacts due to IO error.");
        } catch (RuntimeException e) {
          CLEANUP.warn("Failed to remove S3 artifacts: " + e.getMessage());
          cleanupContext.onBuildCleanupError(this, build, "Failed to remove S3 artifacts due to unexpected error.");
        }
      }
    }
  }

  @Override
  public int getCleanupBuildsDataConcurrencyLevel() {
    return TeamCityProperties.getInteger(CONCURRENCY_LEVEL_PROPERTY, 2);
  }

  private void doClean(@NotNull BuildCleanupContext cleanupContext, @NotNull SFinishedBuild build, @NotNull String pathPrefix, @NotNull List<String> pathsToDelete,
                       @NotNull Map<String, String> storageSettings) throws IOException, InvalidSettingsException {
    String bucketName = S3Util.getBucketName(storageSettings);
    assert bucketName != null;

    String projectId = build.getProjectId();
    SProject project = myProjectManager.findProjectById(projectId);
    if (projectId == null) {
      String errMsg = String.format("Failed to cleanup S3 objects from %s bucket, project is not specified to get correct Connection", bucketName);
      CLEANUP.warn(errMsg);
      cleanupContext.onBuildCleanupError(this, build, errMsg);
      return;
    }

    Map<String, String> projectParameters = project != null ? project.getParameters() : Collections.emptyMap();

    Retrier retrier = Retrier.defaultRetrier(S3Util.getNumberOfRetries(projectParameters), S3Util.getRetryDelayInMs(projectParameters), CLEANUP);

    retrier.registerListener(new AbstractRetrierEventListener() {
      @Override
      public <T> void onFailure(@NotNull Callable<T> callable, int retry, @NotNull Exception e) {
        myCleanupListeners.forEach(listener -> listener.onError(e, true));
      }
    });

    try {
      myAmazonS3Provider.withS3ClientShuttingDownImmediately(
        ParamUtil.putSslValues(myServerPaths, storageSettings),
        projectId,
        client -> {
          String suffix = " from S3 bucket [" + bucketName + "]" + " from path [" + pathPrefix + "]";

          AtomicInteger succeededNum = new AtomicInteger();
          AtomicInteger errorNum = new AtomicInteger();

          int batchSize = TeamCityProperties.getInteger(S3Constants.S3_CLEANUP_BATCH_SIZE, 1000);
          List<List<String>> partitions = Lists.partition(pathsToDelete, batchSize);
          AtomicInteger currentChunk = new AtomicInteger();
          partitions.forEach(part -> {
            try {
              int deletedObjects = retrier.execute(
                () -> deleteChunk(pathPrefix, bucketName, client, part, () -> progressMessage(build, pathsToDelete, succeededNum, currentChunk, partitions.size(), part.size())));
              succeededNum.addAndGet(deletedObjects);
              part.forEach(key -> myCleanupListeners.forEach(listener -> listener.onSuccess(key)));
            } catch (MultiObjectDeleteException e) {
              succeededNum.addAndGet(e.getDeletedObjects().size());

              if (!e.getDeletedObjects().isEmpty()) {
                myCleanupListeners.forEach(listener -> e.getDeletedObjects().forEach(obj -> listener.onSuccess(obj.getKey())));
              }

              List<MultiObjectDeleteException.DeleteError> errors = e.getErrors();
              errors.forEach(error -> {
                String key = error.getKey();
                if (key.startsWith(pathPrefix)) {
                  CLEANUP.info(() -> "Failed to remove " + key + " from S3 bucket " + bucketName + ": " + error.getMessage());
                  pathsToDelete.remove(key.substring(pathPrefix.length()));
                  myCleanupListeners.forEach(listener -> listener.onError(e, false));
                }
              });
              errorNum.addAndGet(errors.size());
            } catch (SdkClientException e) {
              Throwable innerException = e.getCause();
              if (innerException instanceof UnknownHostException) {
                CLEANUP.warnAndDebugDetails("Could not establish connection to AWS server", innerException);
              } else if (innerException != null) {
                CLEANUP.warnAndDebugDetails(EXCEPTION_MESSAGE + part, innerException);
              } else {
                CLEANUP.warnAndDebugDetails(EXCEPTION_MESSAGE + part, e);
              }
              errorNum.addAndGet(part.size());
              myCleanupListeners.forEach(listener -> listener.onError(e, false));
            } catch (Exception e) {
              CLEANUP.warnAndDebugDetails(EXCEPTION_MESSAGE + part, e);
              errorNum.addAndGet(part.size());
              myCleanupListeners.forEach(listener -> listener.onError(e, false));
            }
          });

          if (errorNum.get() > 0) {
            CLEANUP.warn("Failed to remove [" + errorNum + "] S3 " + StringUtil.pluralize("object", errorNum.get()) + suffix);
            cleanupContext.onBuildCleanupError(this, build, "Failed to remove some S3 objects.");
          }

          CLEANUP.info(() -> "Removed [" + succeededNum + "] S3 " + StringUtil.pluralize("object", succeededNum.get()) + suffix);

          myHelper.removeFromArtifactList(build, pathsToDelete);

          return null;
        }
      );
    } catch (ConnectionCredentialsException e) {
      CLEANUP.warnAndDebugDetails(EXCEPTION_MESSAGE + e.getMessage(), e);
    }
  }

  @NotNull
  private String progressMessage(@NotNull SFinishedBuild build, @NotNull List<String> pathsToDelete, @NotNull AtomicInteger succeededNum,
                                 @NotNull AtomicInteger currentChunkNumber, int size, int chunkSize) {
    return String.format(
      "Cleaning artifacts of Build %s: S3Client deleting chunk #%d/%d of %d/%d objects",
      LogUtil.describe(build), currentChunkNumber.incrementAndGet(), size, chunkSize, pathsToDelete.size() - succeededNum.get());
  }

  @NotNull
  private Integer deleteChunk(@NotNull String pathPrefix, @NotNull String bucketName, @NotNull AmazonS3 client, @NotNull List<String> part, @NotNull Supplier<String> info)
    throws Exception {
    List<DeleteObjectsRequest.KeyVersion> objectKeys = part.stream().map(path -> new DeleteObjectsRequest.KeyVersion(pathPrefix + path)).collect(Collectors.toList());
    DeleteObjectsRequest deleteObjectsRequest = new DeleteObjectsRequest(bucketName).withKeys(objectKeys);
    return NamedThreadFactory.executeWithNewThreadName(
      info.get(),
      () -> Util.doUnderContextClassLoader(S3Util.class.getClassLoader(), () -> {
        String keys = CLEANUP.isDebugEnabled() ? deleteObjectsRequest.getKeys().stream().map(DeleteObjectsRequest.KeyVersion::getKey).collect(Collectors.joining()) : "";
        CLEANUP.debug(() -> String.format("Starting to remove %s from S3 bucket %s", keys, deleteObjectsRequest.getBucketName()));
        List<DeleteObjectsResult.DeletedObject> deletedObjects = client.deleteObjects(deleteObjectsRequest).getDeletedObjects();
        CLEANUP.debug(() -> String.format("Finished to remove %s from S3 bucket %s", keys, deleteObjectsRequest.getBucketName()));
        return deletedObjects.size();
      }));
  }

  @VisibleForTesting
  void registerListener(@NotNull CleanupListener listener) {
    myCleanupListeners.add(listener);
  }

  private static class BuildStorageInfo {
    @NotNull
    final SFinishedBuild myBuild;
    @NotNull
    final String myPathPrefix;
    @NotNull
    final List<String> myPathsToDelete;
    @NotNull
    final Map<String, String> myStorageSettings;

    BuildStorageInfo(@NotNull SFinishedBuild build,
                     @NotNull String pathPrefix,
                     @NotNull List<String> pathsToDelete,
                     @NotNull Map<String, String> storageSettings) {
      myBuild = build;
      myPathPrefix = pathPrefix;
      myPathsToDelete = pathsToDelete;
      myStorageSettings = storageSettings;
    }
  }

}
