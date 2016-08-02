package jetbrains.buildServer.artifacts.tree;

import jetbrains.buildServer.Build;
import jetbrains.buildServer.artifacts.ExternalArtifact;
import jetbrains.buildServer.artifacts.utils.ServerExternalArtifactUtil;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.util.browser.ArtifactBrowserProvider;
import jetbrains.buildServer.util.browser.Browser;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
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
    List<ExternalArtifact> artifactList = null;
    try {
      artifactList = ServerExternalArtifactUtil.getExternalArtifacts(sBuild);
    } catch (IOException e) {
      LOG.error("Failed to load external artifacts list.", e);
    }

    if (artifactList != null && artifactList.size() > 0) {
      return new ExternalArtifactsListBrowser(artifactList);
    } else {
      return null;
    }
  }

}
