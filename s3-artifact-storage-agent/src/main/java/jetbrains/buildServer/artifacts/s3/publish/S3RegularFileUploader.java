/*
 * Copyright 2000-2020 JetBrains s.r.o.
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
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.transfer.Upload;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.util.*;
import java.util.concurrent.Callable;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.ArtifactPublishingFailedException;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.agent.ssl.TrustedCertificatesDirectory;
import jetbrains.buildServer.artifacts.ArtifactDataInstance;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.artifacts.s3.SSLParamUtil;
import jetbrains.buildServer.artifacts.s3.retry.*;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import jetbrains.buildServer.util.amazon.AWSException;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.artifacts.s3.S3Util.getBucketName;

public class S3RegularFileUploader implements S3FileUploader {
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
  public Collection<ArtifactDataInstance> publishFiles(@NotNull final AgentRunningBuild build,
                                                       @NotNull final String pathPrefix,
                                                       @NotNull final Map<File, String> filesToPublish) {
    final String homeDir = myBuildAgentConfiguration.getAgentHomeDirectory().getPath();
    final String certDirectory = TrustedCertificatesDirectory.getAllCertificatesDirectoryFromHome(homeDir);
    final int numberOfRetries = S3Util.getNumberOfRetries(build.getSharedConfigParameters());
    final int retryDelay = S3Util.getRetryDelayInMs(build.getSharedConfigParameters());

    final Map<String, String> params = new HashMap<>(S3Util.validateParameters(SSLParamUtil.putSslDirectory(build.getArtifactStorageSettings(), certDirectory)));
    final String bucketName = getBucketName(params);

    try {
      prepareDestination(bucketName, params);
      final List<ArtifactDataInstance> artifacts = new ArrayList<>();
      final Retrier retrier = new RetrierImpl(numberOfRetries)
        .registerListener(new LoggingRetrier(LOG))
        .registerListener(new AbortingListener())
        .registerListener(new RetrierExponentialDelay(retryDelay));

      S3Util.withTransferManagerCorrectingRegion(params, transferManager -> CollectionsUtil
        .convertAndFilterNulls(filesToPublish.entrySet(), entry -> retrier.execute(new Callable<Upload>() {
          @Override
          public String toString() {
            final String filename = entry.getKey() != null ? entry.getKey().getName() : "null";
            return "publishing file '" + filename + "'";
          }

          @Override
          public Upload call() throws AmazonClientException {
            final File file = entry.getKey();
            final String path = entry.getValue();
            if (!file.exists()) {
              build.getBuildLogger().warning("Artifact \"" + file.getAbsolutePath() + "\" does not exist and will not be published to the server");
              return null;
            }
            final String artifactPath = S3Util.normalizeArtifactPath(path, file);
            final String objectKey = pathPrefix + artifactPath;

            artifacts.add(ArtifactDataInstance.create(artifactPath, file.length()));

            final ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(S3Util.getContentType(file));
            final PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, objectKey, file)
              .withCannedAcl(CannedAccessControlList.Private)
              .withMetadata(metadata);
            final Upload upload = transferManager.upload(putObjectRequest);
            try {
              upload.waitForUploadResult();
            } catch (InterruptedException e) {
              throw new RuntimeException(e);
            }
            return upload;
          }
        })));
      return artifacts;
    } catch (ArtifactPublishingFailedException t) {
      throw t;
    } catch (Throwable t) {
      final AWSException awsException = new AWSException(t);
      final String details = awsException.getDetails();

      if (StringUtil.isNotEmpty(details)) {
        final String message = awsException.getMessage() + details;
        LOG.warn(message);
        build.getBuildLogger().error(message);
      }

      throw new ArtifactPublishingFailedException(awsException.getMessage(), false, awsException);
    }
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
