package jetbrains.buildServer.artifacts.utils;

import jetbrains.buildServer.artifacts.ExternalArtifact;
import jetbrains.buildServer.artifacts.utils.ExternalArtifactUtil;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactHolder;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifacts;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactsViewMode;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

/**
 * Created by Nikita.Skvortsov
 * date: 02.08.2016.
 * @since 10.0
 */
public class ServerExternalArtifactUtil {

  /**
   * Get external artifacts information from the build.
   *
   * @param build to get information from
   * @return list of available external artifact. If no external artifacts information is available, the list will be empty
   * @throws IOException in case of errors while reading the artifacts information file
   */
  @NotNull
  public static List<ExternalArtifact> getExternalArtifacts(SBuild build) throws IOException {
    InputStream is = null;
    try {
      final BuildArtifacts artifacts = build.getArtifacts(BuildArtifactsViewMode.VIEW_ALL);
      final BuildArtifactHolder artifact = artifacts.findArtifact(".teamcity" + "/" + "external_artifacts_list.txt");
      if (artifact.isAvailable()) {
        is = artifact.getArtifact().getInputStream();
        return ExternalArtifactUtil.readExternalArtifacts(is);
      } else {
        return Collections.emptyList();
      }
    } finally {
      FileUtil.close(is);
    }
  }
}
