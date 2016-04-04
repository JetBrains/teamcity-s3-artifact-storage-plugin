package jetbrains.buildServer.artifacts.s3.tree;

import jetbrains.buildServer.Build;
import jetbrains.buildServer.artifacts.S3Artifact;
import jetbrains.buildServer.artifacts.s3.utils.S3Util;
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

import static jetbrains.buildServer.artifacts.Constants.*;

/**
 * Created by Nikita.Skvortsov
 * date: 08.02.2016.
 */
public class S3ArtifactsListBrowserProvider implements ArtifactBrowserProvider {
  private static final Logger LOG = Logger.getLogger(S3ArtifactsListBrowserProvider.class);
  @Override
  public Browser getOrCreateBrowser(@NotNull Build build) {
    final SBuild sBuild = (SBuild) build;
    final BuildArtifacts artifacts = sBuild.getArtifacts(BuildArtifactsViewMode.VIEW_ALL);
    final BuildArtifactHolder artifact = artifacts.findArtifact(S3_ARTIFACTS_LIST_PATH + "/" + S3_ARTIFACTS_LIST);
    if (artifact.isAvailable()) {
      InputStream is = null;
      try {
        is = artifact.getArtifact().getInputStream();
        List<S3Artifact> artifactList = S3Util.readS3Artifacts(is);

        if (artifactList.size() > 0) {
          return new S3ArtifactsListBrowser(artifactList);
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
