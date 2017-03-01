package jetbrains.buildServer.artifacts.s3.publish;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import jetbrains.buildServer.ArtifactsConstants;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.agent.artifacts.AgentExternalArtifactHelper;
import jetbrains.buildServer.agent.artifacts.ExternalArtifactsPublisher;
import jetbrains.buildServer.artifacts.ExternalArtifact;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.storage.StorageSettingsProvider;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.amazon.AWSException;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_STORAGE_TYPE;
import static jetbrains.buildServer.artifacts.s3.S3Util.*;

/**
 * Created by Nikita.Skvortsov
 * date: 03.02.2016.
 */
public class S3ArtifactsPublisher extends ExternalArtifactsPublisher {

  private final static Logger LOG = Logger.getLogger(S3ArtifactsPublisher.class);

  @NotNull
  private final CurrentBuildTracker myTracker;

  @NotNull
  private final String myServerUrl;

  public S3ArtifactsPublisher(@NotNull AgentExternalArtifactHelper helper,
                              @NotNull final EventDispatcher<AgentLifeCycleListener> dispatcher,
                              @NotNull final StorageSettingsProvider settingsProvider,
                              @NotNull final CurrentBuildTracker tracker,
                              @NotNull final BuildAgentConfiguration agentConfiguration) {
    super(helper, settingsProvider);
    myTracker = tracker;
    // TODO: cleanup target path on build start
    dispatcher.addListener(new AgentLifeCycleAdapter() {
      @Override
      public void afterAtrifactsPublished(@NotNull AgentRunningBuild runningBuild, @NotNull BuildFinishedStatus status) {
        publishArtifactsList(runningBuild);
      }
    });
    myServerUrl = agentConfiguration.getServerUrl();
  }

  @Override
  public int publishFiles(@NotNull Map<File, String> map) throws ArtifactPublishingFailedException {
    final Map<String, String> params = getPublisherParameters();

    final String bucketName = getBucketName(params);
    final String pathPrefix = getPathPrefix(params);

    int count = 0;
    try {
      final AmazonS3 s3Client = createAmazonClient(params);

      for (Map.Entry<File, String> entry : map.entrySet()) {
        final File file = entry.getKey();
        final String path = entry.getValue();
        if (path.startsWith(ArtifactsConstants.TEAMCITY_ARTIFACTS_DIR)) {
          continue; // do not publish internal artifacts of the build
        }
        s3Client.putObject(
          new PutObjectRequest(
            bucketName,
            pathPrefix + (StringUtil.isEmpty(path) ? "" : path + "/") + file.getName(),
            file).withCannedAcl(CannedAccessControlList.Private)
        );
        count++;
      }
    } catch (Throwable t) {
      final AWSException awsException = new AWSException(t);
      if (StringUtil.isNotEmpty(awsException.getDetails())) {
        LOG.warn(awsException.getDetails());
      }
      throw new ArtifactPublishingFailedException(awsException.getMessage(), false, awsException);
    }
    return count;
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


  private void publishArtifactsList(@NotNull AgentRunningBuild runningBuild) {
    final Map<String, String> params = getPublisherParameters();

    final String bucketName = getBucketName(params);
    final String pathPrefix = getPathPrefix(params);

    try {
      final AmazonS3 s3Client = createAmazonClient(params);

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
          final String url = myServerUrl + S3Constants.S3_ACCESS_CONTROLLER_PATH + "?buildId=" + myTracker.getCurrentBuild().getBuildId() + "&path=" + URLEncoder.encode(path, "UTF-8");
          artifacts.add(new ExternalArtifact(url, path, size,
                                             S3Constants.S3_KEY_ATTR, key,
                                             S3Constants.S3_BUCKET_ATTR, bucketName));
        }
        if (objectListing.isTruncated()) {
          objectListing = s3Client.listNextBatchOfObjects(objectListing);
        } else break;
      }
      publishExternalArtifactsInfo(artifacts);
    } catch (IOException e) {
      final String err = "Error publishing artifacts list:" + e.getMessage();
      runningBuild.getBuildLogger().error(err);
      LOG.error(err, e);
    } catch (Throwable t) {
      final AWSException awsException = new AWSException(t);
      if (StringUtil.isNotEmpty(awsException.getDetails())) {
        runningBuild.getBuildLogger().error(awsException.getDetails());
      }
      runningBuild.getBuildLogger().error(awsException.getMessage());
    }
  }

  @Override
  protected Map<String, String> getPublisherParameters() throws ArtifactPublishingFailedException {
    final Map<String, String> params = super.getPublisherParameters();
    final Map<String, String> invalids = validateParameters(params, false);
    if (invalids.isEmpty()) return params;
    throw new ArtifactPublishingFailedException(joinStrings(invalids.values()), false, null);
  }
}
