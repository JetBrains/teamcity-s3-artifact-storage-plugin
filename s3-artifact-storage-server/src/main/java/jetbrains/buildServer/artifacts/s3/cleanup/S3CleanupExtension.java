/*
 * Copyright 2000-2021 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import jetbrains.buildServer.artifacts.ArtifactListData;
import jetbrains.buildServer.artifacts.ServerArtifactStorageSettingsProvider;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.artifacts.s3.exceptions.InvalidSettingsException;
import jetbrains.buildServer.artifacts.s3.util.ParamUtil;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.artifacts.ServerArtifactHelper;
import jetbrains.buildServer.serverSide.cleanup.BuildCleanupContext;
import jetbrains.buildServer.serverSide.cleanup.BuildCleanupContextEx;
import jetbrains.buildServer.serverSide.cleanup.CleanupExtension;
import jetbrains.buildServer.serverSide.cleanup.CleanupInterruptedException;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.serverSide.impl.cleanup.ArtifactPathsEvaluator;
import jetbrains.buildServer.util.NamedThreadFactory;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.Util;
import jetbrains.buildServer.util.amazon.retry.AbstractRetrierEventListener;
import jetbrains.buildServer.util.amazon.retry.Retrier;
import jetbrains.buildServer.util.positioning.PositionAware;
import jetbrains.buildServer.util.positioning.PositionConstraint;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.log.Loggers.CLEANUP;

public class S3CleanupExtension implements CleanupExtension, PositionAware {

  private static final String EXCEPTION_MESSAGE = "Got an exception while removing artifacts: ";

  @NotNull
  private final ServerArtifactStorageSettingsProvider mySettingsProvider;
  @NotNull
  private final ServerArtifactHelper myHelper;
  @NotNull
  private final ServerPaths myServerPaths;
  private final ProjectManager myProjectManager;
  @NotNull
  private final ExecutorService myExecutorService;
  @NotNull
  private final List<CleanupListener> myCleanupListeners = new CopyOnWriteArrayList<>(); // is filled from tests only

  public S3CleanupExtension(
    @NotNull ServerArtifactHelper helper,
    @NotNull ServerArtifactStorageSettingsProvider settingsProvider,
    @NotNull ServerPaths serverPaths,
    @NotNull ProjectManager projectManager,
    @NotNull ExecutorServices executorServices) {
    myHelper = helper;
    mySettingsProvider = settingsProvider;
    myServerPaths = serverPaths;
    myProjectManager = projectManager;
    myExecutorService = executorServices.getLowPriorityExecutorService();
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "S3 artifacts cleaner";
  }

  @Override
  public void cleanupBuildsData(@NotNull BuildCleanupContext cleanupContext) throws CleanupInterruptedException {
    for (SFinishedBuild build : cleanupContext.getBuilds()) {
      cleanupContext.getCleanupState().throwIfInterrupted();

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

        doClean(cleanupContext, build, pathPrefix, pathsToDelete);
      } catch (InvalidSettingsException e) {
        CLEANUP.warn("Failed to remove S3 artifacts: " + e.getMessage());
        cleanupContext.onBuildCleanupError(this, build, "Failed to remove S3 artifacts due to incorrect storage settings configuration.");
      } catch (IOException e) {
        CLEANUP.warn("Failed to remove S3 artifacts: " + e.getMessage());
        cleanupContext.onBuildCleanupError(this, build, "Failed to remove S3 artifacts due to IO error.");
      } catch (RuntimeException e) {
        CLEANUP.warn("Failed to remove S3 artifacts: " + e.getMessage());
        cleanupContext.onBuildCleanupError(this, build, "Failed to remove S3 artifacts due to unexpected error.");
      }
    }
  }

  private void doClean(@NotNull BuildCleanupContext cleanupContext, @NotNull SFinishedBuild build, @NotNull String pathPrefix, @NotNull List<String> pathsToDelete)
    throws IOException, InvalidSettingsException {
    Map<String, String> params = S3Util.validateParameters(mySettingsProvider.getStorageSettings(build));
    String bucketName = S3Util.getBucketName(params);

    SProject project = myProjectManager.findProjectById(build.getProjectId());

    Map<String, String> projectParameters = project != null ? project.getParameters() : Collections.emptyMap();

    Retrier retrier = Retrier.defaultRetrier(S3Util.getNumberOfRetries(projectParameters), S3Util.getRetryDelayInMs(projectParameters), CLEANUP);

    retrier.registerListener(new AbstractRetrierEventListener() {
      @Override
      public <T> void onFailure(@NotNull Callable<T> callable, int retry, @NotNull Exception e) {
        myCleanupListeners.forEach(listener -> listener.onError(e, true));
      }
    });

    S3Util.withS3ClientShuttingDownImmediately(ParamUtil.putSslValues(myServerPaths, params), client -> {
      String suffix = " from S3 bucket [" + bucketName + "]" + " from path [" + pathPrefix + "]";

      AtomicInteger succeededNum = new AtomicInteger();
      AtomicInteger errorNum = new AtomicInteger();

      int batchSize = TeamCityProperties.getInteger(S3Constants.S3_CLEANUP_BATCH_SIZE, 1000);
      List<List<String>> partitions = Lists.partition(pathsToDelete, batchSize);
      AtomicInteger currentChunk = new AtomicInteger();
      partitions.forEach(part -> {
        try {
          Future<Integer> future = myExecutorService.submit(
            () -> retrier.execute(
              () -> deleteChunk(pathPrefix, bucketName, client, part, () -> progressMessage(build, pathsToDelete, succeededNum, currentChunk, partitions.size(), part.size()))));
          succeededNum.addAndGet(future.get());
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
        } catch (ExecutionException e) {
          Throwable cause = e.getCause();
          if (cause instanceof SdkClientException) {
            Throwable innerException = cause.getCause();
            if (innerException instanceof UnknownHostException) {
              CLEANUP.warnAndDebugDetails("Could not establish connection to AWS server", innerException);
            } else if (innerException != null) {
              CLEANUP.warnAndDebugDetails(EXCEPTION_MESSAGE + part, innerException);
            } else {
              CLEANUP.warnAndDebugDetails(EXCEPTION_MESSAGE + part, cause);
            }
          } else {
            CLEANUP.warnAndDebugDetails(EXCEPTION_MESSAGE + part, cause);
          }
          errorNum.addAndGet(part.size());
          myCleanupListeners.forEach(listener -> listener.onError(e, false));
        } catch (InterruptedException e) {
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
    });
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
    return NamedThreadFactory.executeWithNewThreadName(info.get(),
      () -> Util.doUnderContextClassLoader(S3Util.class.getClassLoader(), () -> {
        String keys = CLEANUP.isDebugEnabled() ? deleteObjectsRequest.getKeys().stream().map(DeleteObjectsRequest.KeyVersion::getKey).collect(Collectors.joining()) : "";
        CLEANUP.debug(() -> String.format("Starting to remove %s from S3 bucket %s", keys, deleteObjectsRequest.getBucketName()));
        List<DeleteObjectsResult.DeletedObject> deletedObjects = client.deleteObjects(deleteObjectsRequest).getDeletedObjects();
        CLEANUP.debug(() -> String.format("Finished to remove %s from S3 bucket %s", keys, deleteObjectsRequest.getBucketName()));
        return deletedObjects.size();
      }));
  }

  @NotNull
  @Override
  public PositionConstraint getConstraint() {
    return PositionConstraint.first();
  }

  @NotNull
  @Override
  public String getOrderId() {
    return S3Constants.S3_STORAGE_TYPE;
  }

  @VisibleForTesting
  void registerListener(@NotNull CleanupListener listener) {
    myCleanupListeners.add(listener);
  }

}
