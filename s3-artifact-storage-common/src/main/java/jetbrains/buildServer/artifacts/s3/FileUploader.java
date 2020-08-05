package jetbrains.buildServer.artifacts.s3;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.transfer.Upload;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import jetbrains.buildServer.artifacts.ArtifactDataInstance;
import jetbrains.buildServer.artifacts.s3.retry.*;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import jetbrains.buildServer.util.amazon.AWSException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.artifacts.s3.S3Util.getBucketName;

public class FileUploader {
  @NotNull
  private static final Logger LOG = Logger.getInstance(FileUploader.class.getName());
  private boolean isDestinationPrepared = false;

  public void uploadFile(final String path, final File file, final Map<String, String> storageSettings) {
    final HashMap<File, String> filesToPublish = new HashMap<>();
    filesToPublish.put(file, path);
    final int nRetries = Integer.parseInt(storageSettings.get("<n-retries>"));
    final int retryDelay = Integer.parseInt(storageSettings.get("<retryDelay>"));
    uploadFiles(storageSettings.get("<path-prefix>"), filesToPublish, null, nRetries, retryDelay, storageSettings, LOG::error);
  }

  public Collection<ArtifactDataInstance> uploadFiles(@NotNull final String pathPrefix,
                                                      @NotNull final Map<File, String> filesToPublish,
                                                      @Nullable final String certDirectory,
                                                      final int numberOfRetries,
                                                      final int retryDelay,
                                                      final Map<String, String> artifactStorageSettings,
                                                      @Nullable final Consumer<String> errorListener) {
    return publishFilesInternal(pathPrefix, filesToPublish, certDirectory, numberOfRetries, retryDelay, artifactStorageSettings, errorListener);
  }

  private Collection<ArtifactDataInstance> publishFilesInternal(@NotNull final String pathPrefix,
                                                                @NotNull final Map<File, String> filesToPublish,
                                                                @Nullable final String certDirectory,
                                                                final int numberOfRetries,
                                                                final int retryDelay,
                                                                final Map<String, String> artifactStorageSettings,
                                                                @Nullable final Consumer<String> errorListener) {
    final Map<String, String> params = new HashMap<>(S3Util.validateParameters(SSLParamUtil.putSslDirectory(artifactStorageSettings, certDirectory)));
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
    } catch (PublishingFailedException t) {
      throw t;
    } catch (Throwable t) {
      final AWSException awsException = new AWSException(t);
      final String details = awsException.getDetails();

      if (StringUtil.isNotEmpty(details)) {
        final String message = awsException.getMessage() + details;
        LOG.warn(message);
        if (errorListener != null) {
          errorListener.accept(message);
        }
      }

      throw new PublishingFailedException(awsException.getMessage(), awsException);
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
      throw new PublishingFailedException("Target S3 artifact bucket " + bucketName + " doesn't exist", null);
    });
  }

}
