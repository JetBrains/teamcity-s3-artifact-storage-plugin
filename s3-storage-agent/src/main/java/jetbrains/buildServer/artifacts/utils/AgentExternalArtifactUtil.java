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

/**
 * Created by Nikita.Skvortsov
 * date: 02.08.2016.
 *
 * @since 10.0
 */
public class AgentExternalArtifactUtil {

  /**
   * Publish information about external artifacts of current build.
   * Use this method to provide information about artifacts uploaded to external storage during the build.
   * <br/>
   * Method will create and publish a file with json-serialized data. Note, that appending to already uploaded
   * file is not supported. Subsequent calls will result in file being overwritten.
   * <br/>
   * The server will use information in this file to display additional nodes in artifacts tree in web UI
   *
   * @param artifacts - list with information about external artifacts
   * @param publisher - publisher transport to use. In production, this should be {@link jetbrains.buildServer.agent.publisher.WebPublisher}
   * @throws IOException in case of errors
   */
  public static void publishExternalArtifactsInfo(@NotNull List<ExternalArtifact> artifacts, @NotNull ArtifactsPublisher publisher) throws IOException {
    File tempDir = null;
    try {
      tempDir = FileUtil.createTempDirectory("artifacts", "list");
      final File tempFile = new File(tempDir, "external_artifacts_list.txt");
      ExternalArtifactUtil.writeExternalArtifacts(artifacts, tempFile);
      final Map<File, String> newArtifacts = new HashMap<File, String>();
      newArtifacts.put(tempFile, ".teamcity");
      publisher.publishFiles(newArtifacts);
    } finally {
      if (tempDir != null) {
        FileUtil.delete(tempDir);
      }
    }
  }
}
