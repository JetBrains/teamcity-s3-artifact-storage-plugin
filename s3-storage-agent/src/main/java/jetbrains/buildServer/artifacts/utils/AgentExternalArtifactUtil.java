package jetbrains.buildServer.artifacts.utils;

import jetbrains.buildServer.agent.ArtifactsPublisher;
import jetbrains.buildServer.artifacts.ExternalArtifact;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_ARTIFACTS_LIST;
import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_ARTIFACTS_LIST_PATH;

/**
 * Created by Nikita.Skvortsov
 * date: 02.08.2016.
 */
public class AgentExternalArtifactUtil {
  public static File publishExternalArtifactsInfo(@NotNull List<ExternalArtifact> artifacts, @NotNull ArtifactsPublisher publisher) throws IOException {
    File tempDir = null;
    try {
      tempDir = FileUtil.createTempDirectory("artifacts", "list");
      final File tempFile = new File(tempDir, "external_artifacts_list.txt");
      ExternalArtifactUtil.writeExternalArtifacts(artifacts, tempFile);
      final Map<File, String> newArtifacts = new HashMap<File, String>();
      newArtifacts.put(tempFile, ".teamcity");
      publisher.publishFiles(newArtifacts);
      return tempDir;
    } finally {
      if (tempDir != null) {
        FileUtil.delete(tempDir);
      }
    }
  }
}
