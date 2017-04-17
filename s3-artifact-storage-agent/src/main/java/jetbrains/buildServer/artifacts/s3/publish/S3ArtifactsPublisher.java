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
import jetbrains.buildServer.util.filters.Filter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static jetbrains.buildServer.artifacts.s3.S3Constants.*;
import static jetbrains.buildServer.artifacts.s3.S3Util.getBucketName;

public class S3ArtifactsPublisher implements ArtifactsPublisher {

  private static final Logger LOG = Logger.getInstance(S3ArtifactsPublisher.class.getName());
  private static final String ERROR_PUBLISHING_ARTIFACTS_LIST = "Error publishing artifacts list";

  private final CurrentBuildTracker myTracker;
  private final AgentArtifactHelper myHelper;
  private boolean isDestinationPrepared = false;
  private List<ArtifactDataInstance> myArtifacts = new ArrayList<ArtifactDataInstance>();

  public S3ArtifactsPublisher(@NotNull final AgentArtifactHelper helper,
                              @NotNull final EventDispatcher<AgentLifeCycleListener> dispatcher,
                              @NotNull final CurrentBuildTracker tracker) {
    myHelper = helper;
    myTracker = tracker;
    dispatcher.addListener(new AgentLifeCycleAdapter() {
      @Override
      public void buildStarted(@NotNull AgentRunningBuild runningBuild) {
        isDestinationPrepared = false;
        myArtifacts.clear();
      }

      @Override
      public void afterAtrifactsPublished(@NotNull AgentRunningBuild runningBuild, @NotNull BuildFinishedStatus status) {
        publishArtifactsList(runningBuild);
      }
    });
  }

  @Override
  public int publishFiles(@NotNull final Map<File, String> map) throws ArtifactPublishingFailedException {
    final List<Map.Entry<File, String>> filesToPublish = CollectionsUtil.filterCollection(map.entrySet(), new Filter<Map.Entry<File, String>>() {
      @Override
      public boolean accept(@NotNull Map.Entry<File, String> entry) {
        return !entry.getValue().startsWith(ArtifactsConstants.TEAMCITY_ARTIFACTS_DIR);
      }
    });

    if (!filesToPublish.isEmpty()) {
      final AgentRunningBuild build = myTracker.getCurrentBuild();
      try {
        final Map<String, String> params = getPublisherParameters();
        final String bucketName = getBucketName(params);
        prepareDestination(bucketName, params, build);

        final String pathPrefix = getPathPrefixProperty(build);
        myArtifacts.addAll(publishArtifacts(bucketName, pathPrefix, params, filesToPublish));
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

    return filesToPublish.size();
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

  @NotNull
  private List<ArtifactDataInstance> publishArtifacts(final String bucketName,
                                                      final String pathPrefix,
                                                      final Map<String, String> params,
                                                      final List<Map.Entry<File, String>> entries) throws Throwable {
    final List<ArtifactDataInstance> artifacts = new ArrayList<ArtifactDataInstance>();
    jetbrains.buildServer.util.amazon.S3Util.withTransferManager(params, new jetbrains.buildServer.util.amazon.S3Util.WithTransferManager<Upload>() {
      @NotNull
      @Override
      public Collection<Upload> run(@NotNull final TransferManager transferManager) throws Throwable {
        return CollectionsUtil.convertAndFilterNulls(entries, new Converter<Upload, Map.Entry<File, String>>() {
          @Override
          public Upload createFrom(@NotNull Map.Entry<File, String> entry) {
            final File file = entry.getKey();
            final String path = entry.getValue();
            final String artifactPath;
            try {
              artifactPath = preparePath(path, file);
            } catch (Throwable e) {
              throw new RuntimeException(e);
            }

            final String objectPath = pathPrefix + artifactPath;

            artifacts.add(ArtifactDataInstance.create(artifactPath, file.length()));
            return transferManager.upload(new PutObjectRequest(
              bucketName,
              objectPath,
              file).withCannedAcl(CannedAccessControlList.Private));
          }
        });
      }
    });
    return artifacts;
  }

  private String preparePath(final String path, final File file) throws IOException, URISyntaxException {
    if (path.startsWith(".."))
      throw new IOException("Attempting to publish artifact outside of build artifacts directory. Specified target path: \""+ path + "\"");

    if (StringUtil.isEmpty(path)) {
      return file.getName();
    } else {
      return new URI(String.format("%s/%s", path, file.getName())).normalize().getPath();
    }
  }

  private void prepareDestination(final String bucketName,
                                  final Map<String, String> params,
                                  final AgentRunningBuild build) throws Throwable {
    if (isDestinationPrepared) return;

    final String pathPrefix = getPathPrefix(build);
    AWSCommonParams.withAWSClients(params, new AWSCommonParams.WithAWSClients<Void, Throwable>() {
      @Nullable
      @Override
      public Void run(@NotNull AWSClients awsClients) throws Throwable {
        final AmazonS3Client s3Client = awsClients.createS3Client();
        if (s3Client.doesBucketExist(bucketName)) {
          if (s3Client.doesObjectExist(bucketName, pathPrefix)) {
            build.getBuildLogger().message("Target S3 artifact path " + pathPrefix + " already exists in the S3 bucket " +
              bucketName + ", will be removed");
            s3Client.deleteObject(bucketName, pathPrefix);
          }
        } else {
          build.getBuildLogger().message("Target S3 artifact bucket " + bucketName + " doesn't exist, will be created");
          s3Client.createBucket(bucketName);
        }
        build.addSharedSystemProperty(S3_PATH_PREFIX_SYSTEM_PROPERTY, pathPrefix);
        isDestinationPrepared = true;
        return null;
      }
    });
  }

  private void publishArtifactsList(AgentRunningBuild build) {
    if (!myArtifacts.isEmpty()) {
      final String pathPrefix = getPathPrefixProperty(build);
      try {
        myHelper.publishArtifactList(myArtifacts, CollectionsUtil.asMap(S3_PATH_PREFIX_ATTR, pathPrefix));
      } catch (IOException e) {
        build.getBuildLogger().error(ERROR_PUBLISHING_ARTIFACTS_LIST + ": " + e.getMessage());
        LOG.warnAndDebugDetails(ERROR_PUBLISHING_ARTIFACTS_LIST + "for build " + LogUtil.describe(build), e);
      }
    }
  }

  @NotNull
  private Map<String, String> getPublisherParameters() {
    return S3Util.validateParameters(myTracker.getCurrentBuild().getArtifactStorageSettings());
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
