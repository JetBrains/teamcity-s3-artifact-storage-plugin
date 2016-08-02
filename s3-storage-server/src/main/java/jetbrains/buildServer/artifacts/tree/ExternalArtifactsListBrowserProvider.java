package jetbrains.buildServer.artifacts.tree;

import jetbrains.buildServer.Build;
import jetbrains.buildServer.artifacts.ExternalArtifact;
import jetbrains.buildServer.artifacts.utils.ExternalArtifactUtil;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactHolder;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifacts;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactsViewMode;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.browser.ArtifactBrowserProvider;
import jetbrains.buildServer.util.browser.Browser;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Created by Nikita.Skvortsov
 * date: 08.02.2016.
 */
public class ExternalArtifactsListBrowserProvider implements ArtifactBrowserProvider {
  private static final Logger LOG = Logger.getLogger(ExternalArtifactsListBrowserProvider.class);
  @Override
  public Browser getOrCreateBrowser(@NotNull Build build) {
    final SBuild sBuild = (SBuild) build;
    final BuildArtifacts artifacts = sBuild.getArtifacts(BuildArtifactsViewMode.VIEW_ALL);
    final BuildArtifactHolder artifact = artifacts.findArtifact(".teamcity" + "/" + "external_artifacts_list.txt");
    if (artifact.isAvailable()) {
      InputStream is = null;
      try {
        is = artifact.getArtifact().getInputStream();
        List<ExternalArtifact> artifactList = ExternalArtifactUtil.readExternalArtifacts(is);

        if (artifactList.size() > 0) {
          return new ExternalArtifactsListBrowser(artifactList);
        }

      } catch (IOException e) {
        LOG.error("Failed to load s3 artifacts list.", e);
      } finally {
        FileUtil.close(is);
      }
    }
    return null;
  }

}
