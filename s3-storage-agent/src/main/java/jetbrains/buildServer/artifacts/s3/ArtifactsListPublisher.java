package jetbrains.buildServer.artifacts.s3;

import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.agent.publisher.WebPublisher;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.FileUtil;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Nikita.Skvortsov
 * date: 27.01.2016.
 */
public class ArtifactsListPublisher implements ArtifactsPublisher {

  private final static Logger LOG = Logger.getLogger(ArtifactsListPublisher.class);

  private final WebPublisher myWebPublisher;

  public ArtifactsListPublisher(@NotNull final WebPublisher webPublisher,
                                @NotNull final EventDispatcher<AgentLifeCycleListener> dispatcher) {
    this.myWebPublisher = webPublisher;
    dispatcher.addListener(new AgentLifeCycleAdapter() {
      @Override
      public void buildStarted(@NotNull final AgentRunningBuild runningBuild) {
      }
    });
  }

  @Override
  public int publishFiles(@NotNull Map<File, String> map) throws ArtifactPublishingFailedException {
    File tempFile = null;
    try {
      tempFile = File.createTempFile("artifacts", "list.txt");
      for (Map.Entry<File, String> entry : map.entrySet()) {
        FileUtil.writeFile(tempFile, entry.getKey().getName() + " -> " + entry.getValue(), "UTF-8");
      }

      final Map<File, String> newArtifacts = new HashMap<File, String>();
      newArtifacts.put(tempFile, ".teamcity/artifactsLists");

      myWebPublisher.publishFiles(newArtifacts);
    } catch (IOException e) {
      LOG.error("Error publishing artifacts list.", e);
    } finally {
      if (tempFile != null) {
        tempFile.delete();
      }
    }
    return map.size();
  }

  @Override
  public boolean isEnabled() {
    return true;
  }
}
