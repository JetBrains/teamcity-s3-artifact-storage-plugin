package jetbrains.buildServer.artifacts.s3;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.regions.Region;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.*;
import com.intellij.util.Function;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.agent.publisher.WebPublisher;
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

  private static final String BUCKET_NAME = "test.1.jetbrains.buildserver.artifacts.s3";
  private static final String S3_ARTIFACTS_LIST = "s3_artifacts_list.txt";

  private final WebPublisher myWebPublisher;
  private final AmazonS3 myS3 = new AmazonS3Client();
  private AgentRunningBuild myRunningBuild;

  public S3ArtifactsPublisher(@NotNull WebPublisher webPublisher,
                              @NotNull final EventDispatcher<AgentLifeCycleListener> dispatcher) {
    myWebPublisher = webPublisher;
    dispatcher.addListener(new AgentLifeCycleAdapter() {

      @Override
      public void buildStarted(@NotNull final AgentRunningBuild runningBuild) {
        myRunningBuild = runningBuild;
      }

      @Override
      public void afterAtrifactsPublished(@NotNull AgentRunningBuild runningBuild, @NotNull BuildFinishedStatus status) {
        publishArtifactsList();
      }
    });

    Region usWest2 = Region.getRegion(US_WEST_2);
    myS3.setRegion(usWest2);
  }

  @Override
  public int publishFiles(@NotNull Map<File, String> map) throws ArtifactPublishingFailedException {
    String prefix = String.valueOf(myRunningBuild.getBuildId());
    try {
      for (Map.Entry<File, String> entry : map.entrySet()) {
        final File file = entry.getKey();
        final String path = entry.getValue();
        myS3.putObject(new PutObjectRequest(BUCKET_NAME, prefix + "/" + path + "/" + file.getName(), file)
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


  private void publishArtifactsList() {
    File tempDir = null;
    Map<String, String> filesList = new HashMap<String, String>();

    ObjectListing objectListing = myS3.listObjects(new ListObjectsRequest()
        .withBucketName(BUCKET_NAME)
        .withPrefix(String.valueOf(myRunningBuild.getBuildId())));

    for (S3ObjectSummary objectSummary : objectListing.getObjectSummaries()) {
      final String key = objectSummary.getKey();
      final String path = key.substring(key.indexOf("/") + 1);

      filesList.put(path, "https://s3-" + US_WEST_2.getName() + ".amazonaws.com/" + BUCKET_NAME + "/" + key);
    }

    try {
      tempDir = FileUtil.createTempDirectory("artifacts", "list");
      final File tempFile = new File(tempDir, S3_ARTIFACTS_LIST);

      final String text = StringUtil.join(filesList.entrySet(), new Function<Map.Entry<String, String>, String>() {
        @Override
        public String fun(Map.Entry<String, String> entry) {
          return entry.getKey() + " -> " + entry.getValue();
        }
      }, "\n");

      FileUtil.writeFile(tempFile, text, "UTF-8");

      final Map<File, String> newArtifacts = new HashMap<File, String>();
      newArtifacts.put(tempFile, ".teamcity");
      myWebPublisher.publishFiles(newArtifacts);
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
