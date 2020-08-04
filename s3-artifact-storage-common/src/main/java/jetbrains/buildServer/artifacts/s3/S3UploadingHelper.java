package jetbrains.buildServer.artifacts.s3;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.transfer.Upload;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import java.io.File;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import jetbrains.buildServer.artifacts.ArtifactDataInstance;
import jetbrains.buildServer.artifacts.migration.BuildSharedConfigurationProperties;
import jetbrains.buildServer.artifacts.s3.migration.PublishingFailedException;
import jetbrains.buildServer.artifacts.s3.retry.*;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import jetbrains.buildServer.util.amazon.AWSException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.artifacts.s3.S3Util.getBucketName;

public class S3UploadingHelper {
  @NotNull
  private static final Logger LOG = Logger.getInstance(S3UploadingHelper.class.getName());
  private static boolean isDestinationPrepared = false;

  @NotNull
  public static Collection<ArtifactDataInstance> publishFiles(@NotNull final String pathPrefix,
                                                              @NotNull final Map<File, String> filesToPublish,
                                                              @Nullable final String certDirectory,
                                                              final int numberOfRetries,
                                                              final int retryDelay,
                                                              final Map<String, String> artifactStorageSettings,
                                                              @Nullable final Consumer<String> errorListener) {
    if (certDirectory == null) {
      LOG.error("Cert directory is null. \n");
      return null;
    }
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

  private static void prepareDestination(final String bucketName,
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

  public static Pair<String, String> getPathPrefix(Map<String, String> buildConfigParametersForMigration,
                              Map<String, String> buildArtifactStorageSettings) {
    final List<String> pathSegments = new ArrayList<>();
    final String prefix = buildArtifactStorageSettings.getOrDefault(S3Constants.S3_PATH_PREFIX_SETTING, "");
    if (!StringUtil.isEmptyOrSpaces(prefix)) {
      pathSegments.add(prefix);
    }
    pathSegments.add(buildConfigParametersForMigration.get(BuildSharedConfigurationProperties.TEAMCITY_PROJECT_ID_PARAM));
    pathSegments.add(buildConfigParametersForMigration.get(BuildSharedConfigurationProperties.BUILD_TYPE_EXTERNAL_ID_KEY));
    pathSegments.add(buildConfigParametersForMigration.get(BuildSharedConfigurationProperties.BUILD_ID_KEY));
    return new Pair(S3Constants.S3_PATH_PREFIX_ATTR, StringUtil.join("/", pathSegments) + "/");
  }
}
