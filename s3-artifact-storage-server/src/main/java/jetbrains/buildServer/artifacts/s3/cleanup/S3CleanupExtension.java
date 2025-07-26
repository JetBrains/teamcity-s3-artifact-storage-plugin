

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
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import jetbrains.buildServer.artifacts.ArtifactListData;
import jetbrains.buildServer.artifacts.ServerArtifactStorageSettingsProvider;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.artifacts.s3.amazonClient.AmazonS3Provider;
import jetbrains.buildServer.artifacts.s3.amazonClient.impl.AmazonS3ProviderImpl;
import jetbrains.buildServer.artifacts.s3.exceptions.InvalidSettingsException;
import jetbrains.buildServer.artifacts.s3.util.ParamUtil;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.artifacts.ServerArtifactHelper;
import jetbrains.buildServer.serverSide.cleanup.BuildCleanupContext;
import jetbrains.buildServer.serverSide.cleanup.BuildCleanupContextEx;
import jetbrains.buildServer.serverSide.cleanup.BuildsCleanupExtension;
import jetbrains.buildServer.serverSide.cleanup.CleanupInterruptedException;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.serverSide.impl.cleanup.ArtifactPathsEvaluator;
import jetbrains.buildServer.util.Disposable;
import jetbrains.buildServer.util.NamedThreadFactory;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.Util;
import jetbrains.buildServer.util.amazon.retry.AmazonRetrier;
import jetbrains.buildServer.util.retry.AbortRetriesException;
import jetbrains.buildServer.util.retry.Retrier;
import jetbrains.buildServer.util.retry.RetrierEventListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.log.Loggers.CLEANUP;

public class S3CleanupExtension implements BuildsCleanupExtension {

  private static final String CONCURRENCY_LEVEL_PROPERTY = "teamcity.cleanup.extensions.s3CleanupExtension.concurrencyLevel";
  private static final String EXCEPTION_MESSAGE = "Got an exception while removing artifacts: ";
  @NotNull
  private static final String BUILD_STORAGE_INFOS_KEY = S3CleanupExtension.class.getName() + ".BUILD_STORAGE_INFOS_KEY";

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
//  private final Map<Long, BuildStorageInfo> myBuildStorageInfos = new ConcurrentHashMap<>();

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
    Map<Long, BuildStorageInfo> buildStorageInfos = new ConcurrentHashMap<>();
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
        buildStorageInfos.put(build.getBuildId(), new BuildStorageInfo(build, pathPrefix, pathsToDelete, storageSettings));
      } catch (IOException e) {
        CLEANUP.warn("Failed to get S3 artifacts list in build " + LogUtil.describe(build) + ": " + e.getMessage());
        cleanupContext.onBuildCleanupError(this, build, "Failed to get S3 artifacts list due to IO error.");
      } catch (RuntimeException e) {
        CLEANUP.warn("Failed to remove S3 artifacts " + LogUtil.describe(build) + ": " + e.getMessage());
        cleanupContext.onBuildCleanupError(this, build, "Failed to remove S3 artifacts due to unexpected error.");
      }
    }
    cleanupContext.setExtensionData(BUILD_STORAGE_INFOS_KEY, buildStorageInfos);
  }

  @Override
  public void cleanupBuildsData(@NotNull BuildCleanupContext cleanupContext) throws CleanupInterruptedException {
    //noinspection unchecked
    Map<Long, BuildStorageInfo> buildStorageInfos = (Map<Long, BuildStorageInfo>)cleanupContext.getExtensionData(BUILD_STORAGE_INFOS_KEY);
    if (buildStorageInfos == null) {
      throw new IllegalStateException("Extension data should have been initialized during `prepareBuildsData` stage.");
    }
    for (long buildId : cleanupContext.getBuildIds()) {
      BuildStorageInfo buildStorageInfo = buildStorageInfos.remove(buildId);
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


    SProject project = findProjectToGetConnection(build);
    if (project == null) {
      String errMsg = String.format("Failed to cleanup S3 objects from %s bucket, project is not specified to get correct Connection", bucketName);
      CLEANUP.warn(errMsg);
      cleanupContext.onBuildCleanupError(this, build, errMsg);
      return;
    }
    String projectId = project.getProjectId();

    Map<String, String> projectParameters = project.getParameters();

    Retrier retrier = AmazonRetrier.defaultAwsRetrier(S3Util.getNumberOfRetries(projectParameters), S3Util.getRetryDelayInMs(projectParameters), CLEANUP);

    retrier.registerListener(new RetrierEventListener() {
      @Override
      public <T> void onFailure(@NotNull Callable<T> callable, int retry, @NotNull Exception e) {
        if (AmazonS3ProviderImpl.isIncorrectRegionOrAccelerationException(e)) {
          throw new AbortRetriesException(e);
        }

        myCleanupListeners.forEach(listener -> listener.onError(e, true));
      }
    });

    Map<String, String> settings = new HashMap<>(storageSettings);
    ParamUtil.putSslValues(myServerPaths, settings);

    String suffix = " from S3 bucket [" + bucketName + "]" + " from path [" + pathPrefix + "]";

    AtomicInteger succeededNum = new AtomicInteger();
    AtomicInteger errorNum = new AtomicInteger();
    AtomicReference<List<String>> currentPart = new AtomicReference<>(Collections.emptyList());
    List<String> pathsFailedToDelete = new ArrayList<>();

    try {
      myAmazonS3Provider.withCorrectingRegionAndAcceleration(settings, projectId, client -> {
        int batchSize = TeamCityProperties.getInteger(S3Constants.S3_CLEANUP_BATCH_SIZE, 1000);
        List<List<String>> partitions = Lists.partition(pathsToDelete, batchSize);
        AtomicInteger processedChunksNum = new AtomicInteger();
        for (List<String> part : partitions) {
          currentPart.set(part);
          try {
            Disposable threadName = NamedThreadFactory.patchThreadName(progressMessage(build, pathsToDelete, succeededNum, processedChunksNum, partitions.size(), part.size()));
            try {
              retrier.execute(() -> succeededNum.addAndGet(deleteChunk(pathPrefix, bucketName, client, part)));
            } finally {
              threadName.dispose();
            }

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
                pathsFailedToDelete.add(key.substring(pathPrefix.length()));
                myCleanupListeners.forEach(listener -> listener.onError(e, false));
              }
            });
            errorNum.addAndGet(errors.size());
          }
        }

        return null;
      }, true);
    } catch (SdkClientException e) {
      Throwable innerException = e.getCause();
      if (innerException instanceof UnknownHostException) {
        CLEANUP.warnAndDebugDetails("Could not establish connection to AWS server", innerException);
      } else if (innerException != null) {
        CLEANUP.warnAndDebugDetails(EXCEPTION_MESSAGE + currentPart.get(), innerException);
      } else {
        CLEANUP.warnAndDebugDetails(EXCEPTION_MESSAGE + currentPart.get(), e);
      }
      errorNum.addAndGet(currentPart.get().size());
      myCleanupListeners.forEach(listener -> listener.onError(e, false));
    } catch (Exception e) {
      CLEANUP.warnAndDebugDetails(EXCEPTION_MESSAGE + currentPart.get(), e);
      errorNum.addAndGet(currentPart.get().size());
      myCleanupListeners.forEach(listener -> listener.onError(e, false));
    }

    if (errorNum.get() > 0) {
      CLEANUP.warn("Failed to remove [" + errorNum + "] S3 " + StringUtil.pluralize("object", errorNum.get()) + suffix);
      cleanupContext.onBuildCleanupError(this, build, "Failed to remove some S3 objects.");
    }

    CLEANUP.info(() -> "Removed [" + succeededNum + "] S3 " + StringUtil.pluralize("object", succeededNum.get()) + suffix);

    if (!pathsFailedToDelete.isEmpty())
      pathsToDelete.removeAll(pathsFailedToDelete);

    myHelper.removeFromArtifactList(build, pathsToDelete);
  }

  @Nullable
  private SProject findProjectToGetConnection(@NotNull SFinishedBuild build) {
    SProject project = myProjectManager.findProjectById(build.getProjectId());
    if (project != null) {
      return project;
    }
    final List<String> projectPathIds = ((BuildPromotionEx)build.getBuildPromotion()).getProjectPathIds();
    if (projectPathIds.isEmpty()) {
      return null;
    }
    ListIterator<String> iterator = projectPathIds.listIterator(projectPathIds.size());
    while (iterator.hasPrevious()) {
      project = myProjectManager.findProjectById(iterator.previous());
      if (project != null) {
        return project;
      }
    }
    return null;
  }

  @NotNull
  private String progressMessage(@NotNull SFinishedBuild build, @NotNull List<String> pathsToDelete, @NotNull AtomicInteger succeededNum,
                                 @NotNull AtomicInteger currentChunkNumber, int size, int chunkSize) {
    return String.format(
      "Cleaning artifacts of Build %s: S3Client deleting chunk #%d/%d of %d/%d objects",
      LogUtil.describe(build), currentChunkNumber.incrementAndGet(), size, chunkSize, pathsToDelete.size() - succeededNum.get());
  }

  private int deleteChunk(@NotNull String pathPrefix, @NotNull String bucketName, @NotNull AmazonS3 client, @NotNull List<String> part) {
    List<DeleteObjectsRequest.KeyVersion> objectKeys = part.stream().map(path -> new DeleteObjectsRequest.KeyVersion(pathPrefix + path)).collect(Collectors.toList());
    DeleteObjectsRequest deleteObjectsRequest = new DeleteObjectsRequest(bucketName).withKeys(objectKeys);
    return Util.doUnderContextClassLoader(S3Util.class.getClassLoader(), () -> {
      String keys = CLEANUP.isDebugEnabled() ? deleteObjectsRequest.getKeys().stream().map(DeleteObjectsRequest.KeyVersion::getKey).collect(Collectors.joining()) : "";
      CLEANUP.debug(() -> String.format("Starting to remove %s from S3 bucket %s", keys, deleteObjectsRequest.getBucketName()));
      List<DeleteObjectsResult.DeletedObject> deletedObjects = client.deleteObjects(deleteObjectsRequest).getDeletedObjects();
      CLEANUP.debug(() -> String.format("Finished to remove %s from S3 bucket %s", keys, deleteObjectsRequest.getBucketName()));
      return deletedObjects.size();
    });
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
