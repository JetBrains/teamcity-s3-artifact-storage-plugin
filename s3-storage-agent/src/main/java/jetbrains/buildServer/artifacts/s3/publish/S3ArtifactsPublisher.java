package jetbrains.buildServer.artifacts.s3.publish;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;
import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.ArtifactsConstants;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.agent.artifacts.AgentExternalArtifactHelper;
import jetbrains.buildServer.agent.artifacts.ExternalArtifactsPublisher;
import jetbrains.buildServer.artifacts.ExternalArtifact;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.log.LogUtil;
import jetbrains.buildServer.storage.StorageSettingsProvider;
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
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_PATH_PREFIX_SYSTEM_PROPERTY;
import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_STORAGE_TYPE;
import static jetbrains.buildServer.artifacts.s3.S3Util.*;

/**
 * Created by Nikita.Skvortsov
 * date: 03.02.2016.
 */
public class S3ArtifactsPublisher extends ExternalArtifactsPublisher {

  private final static Logger LOG = Logger.getInstance(S3ArtifactsPublisher.class.getName());

  public static final String ERROR_PUBLISHING_ARTIFACTS_LIST = "Error publishing artifacts list";

  @NotNull
  private final CurrentBuildTracker myTracker;

  @NotNull
  private final String myServerUrl;

  public S3ArtifactsPublisher(@NotNull final AgentExternalArtifactHelper helper,
                              @NotNull final EventDispatcher<AgentLifeCycleListener> dispatcher,
                              @NotNull final StorageSettingsProvider settingsProvider,
                              @NotNull final CurrentBuildTracker tracker,
                              @NotNull final BuildAgentConfiguration agentConfiguration) {
    super(helper, settingsProvider);
    myTracker = tracker;
    dispatcher.addListener(new AgentLifeCycleAdapter() {
      @Override
      public void buildStarted(@NotNull AgentRunningBuild runningBuild) {
        prepareDestination(runningBuild);
      }

      @Override
      public void afterAtrifactsPublished(@NotNull AgentRunningBuild runningBuild, @NotNull BuildFinishedStatus status) {
        publishArtifactsList(runningBuild);
      }
    });
    myServerUrl = agentConfiguration.getServerUrl();
  }

  @Override
  public int publishFiles(@NotNull final Map<File, String> map) throws ArtifactPublishingFailedException {
    final Map<String, String> params = getPublisherParameters();
    final String bucketName = getBucketName(params);
    final String pathPrefix = getPathPrefixProperty(myTracker.getCurrentBuild());

    try {
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
      if (StringUtil.isNotEmpty(awsException.getDetails())) {
        LOG.warn(awsException.getDetails());
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
    final Map<String, String> params = getPublisherParameters();
    final String bucketName = getBucketName(params);
    final String pathPrefix = getPathPrefixProperty(runningBuild);

    try {
      publishExternalArtifactsInfo(AWSCommonParams.withAWSClients(params, new AWSCommonParams.WithAWSClients<List<ExternalArtifact>, Throwable>() {
        @NotNull
        @Override
        public List<ExternalArtifact> run(@NotNull AWSClients awsClients) throws Throwable {
          final AmazonS3Client s3Client = awsClients.createS3Client();
          final List<ExternalArtifact> artifacts = new ArrayList<ExternalArtifact>();

          ObjectListing objectListing = s3Client.listObjects(
            new ListObjectsRequest()
              .withBucketName(bucketName)
              .withPrefix(pathPrefix));

          while (true) {
            for (S3ObjectSummary objectSummary : objectListing.getObjectSummaries()) {
              final String key = objectSummary.getKey();
              final String path = key.substring(pathPrefix.length());
              final Long size = objectSummary.getSize();
              final String url = myServerUrl + S3Constants.S3_ACCESS_CONTROLLER_PATH + "?buildId=" + runningBuild.getBuildId() + "&path=" + URLEncoder.encode(path, "UTF-8");
              artifacts.add(new ExternalArtifact(url, path, size,
                S3Constants.S3_KEY_ATTR, key,
                S3Constants.S3_BUCKET_ATTR, bucketName));
            }
            if (objectListing.isTruncated()) {
              objectListing = s3Client.listNextBatchOfObjects(objectListing);
            } else break;
          }

          return artifacts;
        }
      }));
    } catch (IOException e) {
      final String err = ERROR_PUBLISHING_ARTIFACTS_LIST + ": " + e.getMessage();
      runningBuild.getBuildLogger().error(err);
      LOG.error(err, e);
    } catch (Throwable t) {
      final AWSException awsException = new AWSException(t);

      LOG.warnAndDebugDetails(ERROR_PUBLISHING_ARTIFACTS_LIST, awsException);

      if (StringUtil.isNotEmpty(awsException.getDetails())) {
        runningBuild.getBuildLogger().error(awsException.getDetails());
      }
      runningBuild.getBuildLogger().error(ERROR_PUBLISHING_ARTIFACTS_LIST + ": " + awsException.getMessage());
    }
  }

  @Override
  protected Map<String, String> getPublisherParameters() throws ArtifactPublishingFailedException {
    final Map<String, String> params = super.getPublisherParameters();
    final Map<String, String> invalids = validateParameters(params, false);
    if (invalids.isEmpty()) return params;
    throw new ArtifactPublishingFailedException(joinStrings(invalids.values()), false, null);
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
          final String pathPrefix = getPathPrefix(s3Client, build, bucketName);
          if (s3Client.doesBucketExist(bucketName)) {
            s3Client.deleteObject(bucketName, pathPrefix);
          } else {
            s3Client.createBucket(bucketName);
          }
          build.addSharedSystemProperty(S3_PATH_PREFIX_SYSTEM_PROPERTY, pathPrefix);
          return null;
        }
      });
    } catch (Throwable t) {
      LOG.warnAndDebugDetails("Failed to create S3 path to store build " + LogUtil.describe(build) + " artifacts", t);
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
  private String getPathPrefix(@NotNull AmazonS3Client s3Client, @NotNull AgentRunningBuild build, @NotNull String bucketName) {
    String prefix = build.getSharedParametersResolver().resolve("%teamcity.project.id%").getResult() + "/" + build.getBuildTypeExternalId() + "/" + build.getBuildNumber();
    if (s3Client.doesObjectExist(bucketName, prefix)) {
      prefix = prefix + "/" + build.getBuildId();
    }
    return prefix + "/";
  }
}
