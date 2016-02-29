package jetbrains.buildServer.artifacts.s3;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.*;
import com.intellij.util.Function;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.agent.publisher.WebPublisher;
import jetbrains.buildServer.artifacts.Constants;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.amazonaws.regions.Regions.US_WEST_2;

/**
 * Created by Nikita.Skvortsov
 * date: 03.02.2016.
 */
public class S3ArtifactsPublisher implements ArtifactsPublisher {

  private final static Logger LOG = Logger.getLogger(ArtifactsListPublisher.class);

  private final WebPublisher myWebPublisher;
  private AgentRunningBuild myRunningBuild;
  private String myBucketName;

  public S3ArtifactsPublisher(@NotNull WebPublisher webPublisher,
                              @NotNull final EventDispatcher<AgentLifeCycleListener> dispatcher) {
    myWebPublisher = webPublisher;
    dispatcher.addListener(new AgentLifeCycleAdapter() {

      @Override
      public void buildStarted(@NotNull final AgentRunningBuild runningBuild) {
        myRunningBuild = runningBuild;
        myBucketName = runningBuild.getSharedConfigParameters().get(Constants.S3_BUCKET_NAME);
      }

      @Override
      public void afterAtrifactsPublished(@NotNull AgentRunningBuild runningBuild, @NotNull BuildFinishedStatus status) {
        final AmazonS3 s3Client = createAmazonClient(myRunningBuild);
        publishArtifactsList(s3Client);
      }
    });
  }

  private AmazonS3 createAmazonClient(AgentRunningBuild build) {
    final Map<String, String> params = build.getSharedConfigParameters();
    final String accessKeyId = params.get(Constants.S3_KEY_ID);
    final String secretAccessKey = params.get(Constants.S3_SECRET_KEY);
    params.get(Constants.S3_BUCKET_NAME);
    Region usWest2 = Region.getRegion(US_WEST_2);

    AmazonS3 s3client = new AmazonS3Client(new BasicAWSCredentials(accessKeyId, secretAccessKey));
    s3client.setRegion(usWest2);
    return s3client;
  }

  @Override
  public int publishFiles(@NotNull Map<File, String> map, boolean isInternalPublishing) throws ArtifactPublishingFailedException {
    if (!Constants.S3_STORAGE_TYPE.equals(myRunningBuild.getSharedConfigParameters().get("teamcity.storage.type"))) {
      return 0;
    }

    final AmazonS3 myS3 = createAmazonClient(myRunningBuild);
    if (isInternalPublishing) {
      return 0;
    }

    String prefix = String.valueOf(myRunningBuild.getBuildId());
    try {
      for (Map.Entry<File, String> entry : map.entrySet()) {
        final File file = entry.getKey();
        final String path = entry.getValue();
        myS3.putObject(new PutObjectRequest(myBucketName, prefix + "/" + path + "/" + file.getName(), file)
            .withCannedAcl(CannedAccessControlList.PublicRead));
      }
    } catch (AmazonServiceException ase) {
      throw new ArtifactPublishingFailedException("Put object request was rejected.", false, ase);
    }
    return map.size();
  }

  @Override
  public boolean isEnabled() {
    return true;
  }


  private void publishArtifactsList(@NotNull AmazonS3 s3client) {
    File tempDir = null;
    Map<String, String> filesList = new HashMap<String, String>();

    ObjectListing objectListing = s3client.listObjects(new ListObjectsRequest()
        .withBucketName(myBucketName)
        .withPrefix(String.valueOf(myRunningBuild.getBuildId())));

    for (S3ObjectSummary objectSummary : objectListing.getObjectSummaries()) {
      final String key = objectSummary.getKey();
      final String path = key.substring(key.indexOf("/") + 1);

      filesList.put(path, "https://s3-" + US_WEST_2.getName() + ".amazonaws.com/" + myBucketName + "/" + key);
    }

    try {
      tempDir = FileUtil.createTempDirectory("artifacts", "list");
      final File tempFile = new File(tempDir, jetbrains.buildServer.artifacts.Constants.S3_ARTIFACTS_LIST);

      final String text = StringUtil.join(filesList.entrySet(), new Function<Map.Entry<String, String>, String>() {
        @Override
        public String fun(Map.Entry<String, String> entry) {
          return entry.getKey() + " -> " + entry.getValue();
        }
      }, "\n");

      FileUtil.writeFile(tempFile, text, "UTF-8");

      final Map<File, String> newArtifacts = new HashMap<File, String>();
      newArtifacts.put(tempFile, "");
      myWebPublisher.publishFiles(newArtifacts, true);
    } catch (IOException e) {
      LOG.error("Error publishing artifacts list.", e);
    } finally {
      if (tempDir != null) {
        FileUtil.delete(tempDir);
      }
      filesList.clear();
    }
  }
}
