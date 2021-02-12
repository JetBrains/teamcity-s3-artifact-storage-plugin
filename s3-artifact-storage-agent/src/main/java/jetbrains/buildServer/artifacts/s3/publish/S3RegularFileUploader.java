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

package jetbrains.buildServer.artifacts.s3.publish;

import com.amazonaws.AmazonClientException;
import com.amazonaws.event.ProgressEvent;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.transfer.PersistableTransfer;
import com.amazonaws.services.s3.transfer.Upload;
import com.amazonaws.services.s3.transfer.internal.S3ProgressListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.ArtifactPublishingFailedException;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.agent.ssl.TrustedCertificatesDirectory;
import jetbrains.buildServer.artifacts.ArtifactDataInstance;
import jetbrains.buildServer.artifacts.s3.InvalidSettingsException;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.artifacts.s3.SSLParamUtil;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import jetbrains.buildServer.util.amazon.AWSException;
import jetbrains.buildServer.util.amazon.S3Util.S3AdvancedConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.artifacts.s3.S3Util.getBucketName;

public class S3RegularFileUploader extends S3FileUploader {
  @NotNull
  private static final Logger LOG = Logger.getInstance(S3RegularFileUploader.class.getName());
  @NotNull
  private final BuildAgentConfiguration myBuildAgentConfiguration;
  private boolean isDestinationPrepared = false;

  public S3RegularFileUploader(@NotNull final BuildAgentConfiguration buildAgentConfiguration) {
    myBuildAgentConfiguration = buildAgentConfiguration;
  }

  @NotNull
  @Override
  public Collection<ArtifactDataInstance> publish(@NotNull final AgentRunningBuild build,
                                                  @NotNull final String pathPrefix,
                                                  @NotNull final Map<File, String> filesToPublish) throws InvalidSettingsException {
    final BuildProgressLogger buildLog = build.getBuildLogger();
    final String homeDir = myBuildAgentConfiguration.getAgentHomeDirectory().getPath();
    final String certDirectory = TrustedCertificatesDirectory.getAllCertificatesDirectoryFromHome(homeDir);

    final Map<String, String> params = new HashMap<>(S3Util.validateParameters(SSLParamUtil.putSslDirectory(build.getArtifactStorageSettings(), certDirectory)));
    final String bucketName = getBucketName(params);

    try {
      prepareDestination(bucketName, params);
      final List<ArtifactDataInstance> artifacts = new ArrayList<>();
      final S3AdvancedConfiguration configuration = configuration(build.getSharedConfigParameters());

      S3Util.withTransferManagerCorrectingRegion(params, transferManager ->
        filesToPublish.entrySet()
                      .stream()
                      .map(entry -> createRequest(buildLog, pathPrefix, bucketName, artifacts, new Pair<>(entry.getValue(), entry.getKey())))
                      .filter(Objects::nonNull)
                      .map(request -> doUpload(buildLog, transferManager, request))
                      .collect(Collectors.toList()), configuration).forEach(upload -> {
        try {
          upload.waitForCompletion();
        } catch (Exception e) {
          LOG.infoAndDebugDetails("Got exception while waiting for upload completion", e);
          buildLog.message("Got error while waiting for async artifact upload " + e.getMessage());
        }
      });
      return artifacts;
    } catch (ArtifactPublishingFailedException t) {
      throw t;
    } catch (Throwable t) {
      final AWSException awsException = new AWSException(t);
      final String details = awsException.getDetails();

      if (StringUtil.isNotEmpty(details)) {
        final String message = awsException.getMessage() + details;
        LOG.warn(message);
        buildLog.error(message);
      }

      throw new ArtifactPublishingFailedException(awsException.getMessage(), false, awsException);
    }
  }

  private Upload doUpload(@NotNull final BuildProgressLogger buildLog,
                          @NotNull final com.amazonaws.services.s3.transfer.TransferManager transferManager,
                          @NotNull final PutObjectRequest request) {
    try {
      return transferManager.upload(request, new S3ProgressListener() {
        final AtomicLong fileSize = new AtomicLong(request.getFile().length());
        final AtomicInteger reportCounter = new AtomicInteger(0);
        final AtomicBoolean isPersistableTransfer = new AtomicBoolean();

        @Override
        public void onPersistableTransfer(PersistableTransfer persistableTransfer) {
          isPersistableTransfer.set(true);
        }

        @Override
        public void progressChanged(ProgressEvent progressEvent) {
          if (isPersistableTransfer.get() && progressEvent.getEventType().isByteCountEvent()) {
            final int percentage = 100 - (int)Math.round((fileSize.getAndAdd(-progressEvent.getBytesTransferred()) * 100.) / request.getFile().length());
            if (percentage >= reportCounter.get() + 10) {
              buildLog.debug("S3 Multipart Uploading [" + request.getFile().getName() + "] " + percentage + "%");
              reportCounter.set(percentage);
            }
          }
        }
      });
    } catch (AmazonClientException e) {
      buildLog
        .message("Artifact upload " + request.getFile().getName() + " with key " + request.getKey() + " to " + request.getBucketName() + " failed with error " + e.getMessage(),
                 Status.WARNING);
      throw e;
    }
  }

  @Nullable
  private PutObjectRequest createRequest(@NotNull final BuildProgressLogger buildLog,
                                         @NotNull final String pathPrefix,
                                         @NotNull final String bucketName,
                                         @NotNull final List<ArtifactDataInstance> artifacts,
                                         @NotNull final Pair<String, File> fileWithPath) {
    final File file = fileWithPath.getSecond();
    if (!file.exists()) {
      buildLog.warning("Artifact \"" + file.getAbsolutePath() + "\" does not exist and will not be published to the server");
      return null;
    }
    final String artifactPath = S3Util.normalizeArtifactPath(fileWithPath.getFirst(), file);
    final String objectKey = pathPrefix + artifactPath;

    artifacts.add(ArtifactDataInstance.create(artifactPath, file.length()));

    final ObjectMetadata metadata = new ObjectMetadata();
    metadata.setContentType(S3Util.getContentType(file));
    return new PutObjectRequest(bucketName, objectKey, file)
      .withCannedAcl(CannedAccessControlList.Private)
      .withMetadata(metadata);
  }

  private void prepareDestination(final String bucketName,
                                  final Map<String, String> params) throws Throwable {
    if (isDestinationPrepared) return;
    try {
      if ("US".equals(params.get(AWSCommonParams.REGION_NAME_PARAM))) {
        params.put(AWSCommonParams.REGION_NAME_PARAM, "us-east-1");
      }
    } catch (Exception e) {
      LOG.infoAndDebugDetails("Attempt to correct aws region from US to us-east-1 failed", e);
    }

    S3Util.withS3ClientShuttingDownImmediately(params, (S3Util.WithS3<Void, Throwable>)s3Client -> {
      if (s3Client.doesBucketExistV2(bucketName)) {
        isDestinationPrepared = true;
        return null;
      }
      throw new ArtifactPublishingFailedException("Target S3 artifact bucket " + bucketName + " doesn't exist", false, null);
    });
  }
}
