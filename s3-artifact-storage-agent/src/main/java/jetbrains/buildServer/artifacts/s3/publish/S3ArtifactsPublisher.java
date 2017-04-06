package jetbrains.buildServer.artifacts.s3.publish;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;
import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.ArtifactsConstants;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.agent.artifacts.AgentArtifactHelper;
import jetbrains.buildServer.artifacts.ArtifactDataInstance;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.log.LogUtil;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.amazon.AWSClients;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import jetbrains.buildServer.util.amazon.AWSException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static jetbrains.buildServer.artifacts.s3.S3Constants.*;
import static jetbrains.buildServer.artifacts.s3.S3Util.getBucketName;

public class S3ArtifactsPublisher implements ArtifactsPublisher {

  private final static Logger LOG = Logger.getInstance(S3ArtifactsPublisher.class.getName());

  public static final String ERROR_PUBLISHING_ARTIFACTS_LIST = "Error publishing artifacts list";

  @NotNull
  private final CurrentBuildTracker myTracker;
  private final AgentArtifactHelper myHelper;

  public S3ArtifactsPublisher(@NotNull final AgentArtifactHelper helper,
                              @NotNull final EventDispatcher<AgentLifeCycleListener> dispatcher,
                              @NotNull final CurrentBuildTracker tracker) {
    myTracker = tracker;
    myHelper = helper;
    dispatcher.addListener(new AgentLifeCycleAdapter() {
      @Override
      public void buildStarted(@NotNull AgentRunningBuild runningBuild) {
        if (isPublishingEnabled()) {
          prepareDestination(runningBuild);
        }
      }

      @Override
      public void afterAtrifactsPublished(@NotNull AgentRunningBuild runningBuild, @NotNull BuildFinishedStatus status) {
        if (isPublishingEnabled()) {
          publishArtifactsList(runningBuild);
        }
      }
    });
  }

  @Override
  public int publishFiles(@NotNull final Map<File, String> map) throws ArtifactPublishingFailedException {
    try {
      final Map<String, String> params = getPublisherParameters();
      final String bucketName = getBucketName(params);
      final String pathPrefix = getPathPrefixProperty(myTracker.getCurrentBuild());

      return jetbrains.buildServer.util.amazon.S3Util.withTransferManager(params, new jetbrains.buildServer.util.amazon.S3Util.WithTransferManager<Upload>() {
        @NotNull
        @Override
        public Collection<Upload> run(@NotNull final TransferManager transferManager) throws Throwable {
          return CollectionsUtil.convertAndFilterNulls(map.entrySet(), new Converter<Upload, Map.Entry<File, String>>() {
            @Override
            public Upload createFrom(@NotNull Map.Entry<File, String> entry) {
              final File file = entry.getKey();
              final String path = entry.getValue();
              if (path.startsWith(ArtifactsConstants.TEAMCITY_ARTIFACTS_DIR)) {
                return null; // do not publish internal artifacts of the build
              }
              return transferManager.upload(new PutObjectRequest(
                bucketName,
                pathPrefix + (StringUtil.isEmpty(path) ? "" : path + "/") + file.getName(),
                file).withCannedAcl(CannedAccessControlList.Private));
            }
          });
        }
      }).size();
    } catch (Throwable t) {
      final AWSException awsException = new AWSException(t);

      final String details = awsException.getDetails();
      if (StringUtil.isNotEmpty(details)) {
        LOG.warn(details);
        myTracker.getCurrentBuild().getBuildLogger().error(details);
      }
      throw new ArtifactPublishingFailedException(awsException.getMessage(), false, awsException);
    }
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @NotNull
  @Override
  public String getType() {
    return S3_STORAGE_TYPE;
  }


  private void publishArtifactsList(@NotNull final AgentRunningBuild runningBuild) {
    try {
      final Map<String, String> params = getPublisherParameters();
      final String bucketName = getBucketName(params);
      final String pathPrefix = getPathPrefixProperty(runningBuild);

      myHelper.publishArtifactList(AWSCommonParams.withAWSClients(params, new AWSCommonParams.WithAWSClients<List<ArtifactDataInstance>, Throwable>() {
        @NotNull
        @Override
        public List<ArtifactDataInstance> run(@NotNull AWSClients awsClients) throws Throwable {
          final AmazonS3Client s3Client = awsClients.createS3Client();
          final List<ArtifactDataInstance> artifacts = new ArrayList<ArtifactDataInstance>();

          ObjectListing objectListing = s3Client.listObjects(
            new ListObjectsRequest()
              .withBucketName(bucketName)
              .withPrefix(pathPrefix));

          while (true) {
            for (S3ObjectSummary objectSummary : objectListing.getObjectSummaries()) {
              final String path = objectSummary.getKey().substring(pathPrefix.length());
              final Long size = objectSummary.getSize();
              artifacts.add(ArtifactDataInstance.create(path, size));
            }
            if (objectListing.isTruncated()) {
              objectListing = s3Client.listNextBatchOfObjects(objectListing);
            } else break;
          }

          return artifacts;
        }
      }), CollectionsUtil.asMap(S3_PATH_PREFIX_ATTR, pathPrefix));
    } catch (IOException e) {
      LOG.warnAndDebugDetails(ERROR_PUBLISHING_ARTIFACTS_LIST + " for build " + LogUtil.describe(runningBuild), e);
      runningBuild.getBuildLogger().error(ERROR_PUBLISHING_ARTIFACTS_LIST + ": " + e.getMessage());
    } catch (Throwable t) {
      final AWSException awsException = new AWSException(t);

      runningBuild.getBuildLogger().error(ERROR_PUBLISHING_ARTIFACTS_LIST + ": " + awsException.getMessage());

      final String details = awsException.getDetails();
      if (StringUtil.isNotEmpty(details)) {
        LOG.warn(details);
        runningBuild.getBuildLogger().error(details);
      }

      LOG.warnAndDebugDetails(ERROR_PUBLISHING_ARTIFACTS_LIST + "for build " + LogUtil.describe(runningBuild), awsException);
    }
  }

  private Map<String, String> getPublisherParameters() {
    return S3Util.validateParameters(myTracker.getCurrentBuild().getArtifactStorageSettings());
  }

  private boolean isPublishingEnabled() {
    return !getPublisherParameters().isEmpty();
  }

  private void prepareDestination(@NotNull final AgentRunningBuild build) {
    final Map<String, String> params = getPublisherParameters();
    final String bucketName = getBucketName(params);

    try {
      AWSCommonParams.withAWSClients(params, new AWSCommonParams.WithAWSClients<Void, Throwable>() {
        @Nullable
        @Override
        public Void run(@NotNull AWSClients awsClients) throws Throwable {
          final AmazonS3Client s3Client = awsClients.createS3Client();
          final String pathPrefix = getPathPrefix(build);
          if (s3Client.doesBucketExist(bucketName)) {
            if (s3Client.doesObjectExist(bucketName, pathPrefix)) {
              build.getBuildLogger().message("Target S3 artifact path " + pathPrefix + " already exists in the S3 bucket " + bucketName + ", will be removed");
              s3Client.deleteObject(bucketName, pathPrefix);
            }
          } else {
            build.getBuildLogger().message("Target S3 artifact bucket " + bucketName + " doesn't exist, will be created");
            s3Client.createBucket(bucketName);
          }
          build.addSharedSystemProperty(S3_PATH_PREFIX_SYSTEM_PROPERTY, pathPrefix);
          return null;
        }
      });
    } catch (Throwable t) {
      final AWSException awsException = new AWSException(t);

      final String details = awsException.getDetails();
      if (StringUtil.isNotEmpty(details)) {
        LOG.warn(details);
        build.getBuildLogger().error(details);
      }
      LOG.warnAndDebugDetails("Failed to create S3 path to store build " + LogUtil.describe(build) + " artifacts", awsException);
      build.getBuildLogger().error("Failed to create S3 path to store build artifacts: " + awsException.getMessage());
    }
  }

  @NotNull
  private String getPathPrefixProperty(@NotNull AgentRunningBuild build) throws ArtifactPublishingFailedException {
    final String prefix = build.getSharedBuildParameters().getSystemProperties().get(S3_PATH_PREFIX_SYSTEM_PROPERTY);
    if (StringUtil.isEmptyOrSpaces(prefix)) {
      throw new ArtifactPublishingFailedException("No " + S3_PATH_PREFIX_SYSTEM_PROPERTY + " build system property found", false, null);
    }
    return prefix;
  }

  @NotNull
  private String getPathPrefix(@NotNull AgentRunningBuild build) {
    return build.getSharedConfigParameters().get(ServerProvidedProperties.TEAMCITY_PROJECT_ID_PARAM) + "/" + build.getBuildTypeExternalId() + "/" + build.getBuildId() + "/";
  }
}
