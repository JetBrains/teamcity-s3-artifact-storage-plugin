package jetbrains.buildServer.artifacts.s3.publish;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;
import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.ArtifactPublishingFailedException;
import jetbrains.buildServer.artifacts.ArtifactDataInstance;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.amazon.AWSException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static jetbrains.buildServer.artifacts.s3.S3Util.getBucketName;

public class S3RegularFileUploader implements S3FileUploader {

  private static final Logger LOG = Logger.getInstance(S3RegularFileUploader.class.getName());

  private boolean isDestinationPrepared = false;

  @NotNull
  @Override
  public Collection<ArtifactDataInstance> publishFiles(@NotNull final AgentRunningBuild build,
                                                       @NotNull final String pathPrefix,
                                                       @NotNull final Map<File, String> filesToPublish) {
    final Map<String, String> params = S3Util.validateParameters(build.getArtifactStorageSettings());
    final String bucketName = getBucketName(params);

    try {
      prepareDestination(bucketName, params, build, pathPrefix);
      final List<ArtifactDataInstance> artifacts = new ArrayList<ArtifactDataInstance>();
      jetbrains.buildServer.util.amazon.S3Util.withTransferManager(params, new jetbrains.buildServer.util.amazon.S3Util.WithTransferManager<Upload>() {
        @NotNull
        @Override
        public Collection<Upload> run(@NotNull final TransferManager transferManager) {
          return CollectionsUtil.convertAndFilterNulls(filesToPublish.entrySet(), new Converter<Upload, Map.Entry<File, String>>() {
            @Override
            public Upload createFrom(@NotNull Map.Entry<File, String> entry) {
              final File file = entry.getKey();
              final String path = entry.getValue();
              final String artifactPath = S3Util.normalizeArtifactPath(path, file);
              final String objectKey = pathPrefix + artifactPath;

              artifacts.add(ArtifactDataInstance.create(artifactPath, file.length()));

              final ObjectMetadata metadata = new ObjectMetadata();
              metadata.setContentType(S3Util.getContentType(file));
              final PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, objectKey, file)
                .withCannedAcl(CannedAccessControlList.Private)
                .withMetadata(metadata);

              return transferManager.upload(putObjectRequest);
            }
          });
        }
      });
      return artifacts;
    } catch (ArtifactPublishingFailedException t) {
      throw t;
    } catch (Throwable t) {
      final AWSException awsException = new AWSException(t);
      final String details = awsException.getDetails();

      if (StringUtil.isNotEmpty(details)) {
        LOG.warn(details);
        build.getBuildLogger().error(details);
      }

      throw new ArtifactPublishingFailedException(awsException.getMessage(), false, awsException);
    }
  }

  private void prepareDestination(final String bucketName,
                                  final Map<String, String> params,
                                  final AgentRunningBuild build,
                                  final String pathPrefix) throws Throwable {
    if (isDestinationPrepared) return;

    S3Util.withS3Client(params, new S3Util.WithS3<Void, Throwable>() {
      @Nullable
      @Override
      public Void run(@NotNull AmazonS3 s3Client) {
        // Minio does not support #doesBucketExistsV2
        // noinspection deprecation
        if (s3Client.doesBucketExist(bucketName)) {
          build.getBuildLogger().message("Artifacts are published to the S3 path " + pathPrefix + " in the S3 bucket " + bucketName);
          isDestinationPrepared = true;
          return null;
        }
        throw new ArtifactPublishingFailedException("Target S3 artifact bucket " + bucketName + " doesn't exist", false, null);
      }
    });
  }
}
