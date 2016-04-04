package jetbrains.buildServer.artifacts.s3.publish;

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

/**
 * Created by Nikita.Skvortsov
 * date: 27.01.2016.
 */
public class ArtifactsListPublisher implements ArtifactsPublisher {

  private final static Logger LOG = Logger.getLogger(ArtifactsListPublisher.class);

  private final WebPublisher myWebPublisher;
  private final Map<File, String> myAllFiles = new HashMap<File, String>();

  public ArtifactsListPublisher(@NotNull final WebPublisher webPublisher,
                                @NotNull final EventDispatcher<AgentLifeCycleListener> dispatcher) {
    this.myWebPublisher = webPublisher;
    dispatcher.addListener(new AgentLifeCycleAdapter() {
      @Override
      public void buildStarted(@NotNull final AgentRunningBuild runningBuild) {
        resetArtifactsList();
      }

      @Override
      public void afterAtrifactsPublished(@NotNull AgentRunningBuild runningBuild, @NotNull BuildFinishedStatus status) {
        publishArtifactsList();
      }
    });
  }

  private void resetArtifactsList() {
    myAllFiles.clear();
  }

  private void publishArtifactsList() {
    File tempDir = null;
    try {
      tempDir = FileUtil.createTempDirectory("artifacts", "list");
      final File tempFile = new File(tempDir, "artifacts_list.txt");

      final String text = StringUtil.join(myAllFiles.entrySet(), new Function<Map.Entry<File, String>, String>() {
        @Override
        public String fun(Map.Entry<File, String> entry) {
          return entry.getKey().getName() + " -> " + entry.getValue();
        }
      }, "\n");

      FileUtil.writeFile(tempFile, text, "UTF-8");

      final Map<File, String> newArtifacts = new HashMap<File, String>();
      newArtifacts.put(tempFile, ".teamcity");
      myWebPublisher.publishFiles(newArtifacts, true);
    } catch (IOException e) {
      LOG.error("Error publishing artifacts list.", e);
    } finally {
      if (tempDir != null) {
        FileUtil.delete(tempDir);
      }
      myAllFiles.clear();
    }
  }

  @Override
  public int publishFiles(@NotNull Map<File, String> map, boolean isInternalPublishing) throws ArtifactPublishingFailedException {
    myAllFiles.putAll(map);
    return map.size();
  }

  @Override
  public boolean isEnabled() {
    return true;
  }
}
