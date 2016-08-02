package jetbrains.buildServer.artifacts.s3.publish;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.agent.publisher.WebPublisher;
import jetbrains.buildServer.artifacts.ExternalArtifact;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.utils.S3Util;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static jetbrains.buildServer.artifacts.s3.S3Constants.*;

/**
 * Created by Nikita.Skvortsov
 * date: 03.02.2016.
 */
public class S3ArtifactsPublisher implements ArtifactsPublisher {

  private final static Logger LOG = Logger.getLogger(S3ArtifactsPublisher.class);

  private final WebPublisher myWebPublisher;
  private AgentRunningBuild myRunningBuild;
  private String myBucketName;
  private String myPathPrefix;

  public S3ArtifactsPublisher(@NotNull WebPublisher webPublisher,
                              @NotNull final EventDispatcher<AgentLifeCycleListener> dispatcher) {
    myWebPublisher = webPublisher;
    dispatcher.addListener(new AgentLifeCycleAdapter() {

      @Override
      public void buildStarted(@NotNull final AgentRunningBuild runningBuild) {
        myRunningBuild = runningBuild;
        myBucketName = runningBuild.getSharedConfigParameters().get(S3_BUCKET_NAME);
        String pathPrefix = runningBuild.getSharedConfigParameters().get(S3_PATH_PREFIX);
        pathPrefix = pathPrefix.endsWith("/") ? pathPrefix : pathPrefix + "/";
        pathPrefix = pathPrefix.startsWith("/") ? pathPrefix.substring(1) : pathPrefix;
        myPathPrefix = pathPrefix;
      }

      @Override
      public void afterAtrifactsPublished(@NotNull AgentRunningBuild runningBuild, @NotNull BuildFinishedStatus status) {
        final AmazonS3 s3Client = S3Util.createAmazonClient(myRunningBuild.getSharedConfigParameters());
        publishArtifactsList(s3Client);
      }
    });
  }

  @Override
  public int publishFiles(@NotNull Map<File, String> map) throws ArtifactPublishingFailedException {
    final AmazonS3 myS3 = S3Util.createAmazonClient(myRunningBuild.getSharedConfigParameters());
    final boolean isPublic = Boolean.valueOf(myRunningBuild.getSharedConfigParameters().get(S3_ALLOW_PUBLIC));
    int count = 0;
    try {
      for (Map.Entry<File, String> entry : map.entrySet()) {
        final File file = entry.getKey();
        final String path = entry.getValue();
        if (path.startsWith(".teamcity/")) {
          continue; // do not publish internal artifacts of the build
        }
        myS3.putObject(new PutObjectRequest(myBucketName, myPathPrefix + (StringUtil.isEmpty(path) ? "" : path  + "/") + file.getName(), file)
            .withCannedAcl(isPublic ? CannedAccessControlList.PublicRead : CannedAccessControlList.Private));
        count++;
      }
    } catch (AmazonServiceException ase) {
      throw new ArtifactPublishingFailedException("Put object request was rejected.", false, ase);
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


  private void publishArtifactsList(@NotNull AmazonS3 s3client) {
    File tempDir = null;
    ObjectListing objectListing = s3client.listObjects(new ListObjectsRequest()
        .withBucketName(myBucketName)
        .withPrefix(myPathPrefix));

    try {
      tempDir = FileUtil.createTempDirectory("artifacts", "list");
      final File tempFile = new File(tempDir, S3_ARTIFACTS_LIST);

      final String host = getHost(myRunningBuild.getSharedConfigParameters().get(S3_REGION));
      final List<ExternalArtifact> artifacts = new ArrayList<ExternalArtifact>();

      for (S3ObjectSummary objectSummary : objectListing.getObjectSummaries()) {
        final String key = objectSummary.getKey();
        final String path = key.substring(myPathPrefix.length());
        final Long size = objectSummary.getSize();
        try {
          artifacts.add(new ExternalArtifact(new URI("https", host, "/" + myBucketName + "/" + key, null).toString(), path, size, S3Constants.S3_KEY, key));
        } catch (URISyntaxException e) {
          LOG.warn("Failed to write object [" + key + "] to index", e);
        }
      }

      S3Util.writeExternalArtifacts(artifacts, tempFile);

      final Map<File, String> newArtifacts = new HashMap<File, String>();
      newArtifacts.put(tempFile, S3_ARTIFACTS_LIST_PATH);
      myWebPublisher.publishFiles(newArtifacts);
    } catch (IOException e) {
      LOG.error("Error publishing artifacts list.", e);
    } finally {
      if (tempDir != null) {
        FileUtil.delete(tempDir);
      }
    }
  }

  @NotNull
  private String getHost(String regionName) {
    if ("us-east-1".equals(regionName)) {
      return "s3.amazonaws.com";
    }
    return "s3-" + regionName + ".amazonaws.com";
  }
}
