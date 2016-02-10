package jetbrains.buildServer.artifacts;

import com.intellij.openapi.util.io.StreamUtil;
import jetbrains.buildServer.Build;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactHolder;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifacts;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactsViewMode;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.browser.Browser;
import jetbrains.buildServer.util.browser.BrowserProvider;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by Nikita.Skvortsov
 * date: 08.02.2016.
 */
public class S3ArtifactsListBrowserProvider implements BrowserProvider {
  private static final Logger LOG = Logger.getLogger(S3ArtifactsListBrowserProvider.class);
  @Override
  public Browser getOrCreateBrowser(@NotNull Build build) {
    final SBuild sBuild = (SBuild) build;
    final BuildArtifacts artifacts = sBuild.getArtifacts(BuildArtifactsViewMode.VIEW_ALL);
    final BuildArtifactHolder artifact = artifacts.findArtifact(Constants.S3_ARTIFACTS_LIST);
    if (artifact.isAvailable()) {
      InputStream is = null;
      try {
        is = artifact.getArtifact().getInputStream();
        final String data = StreamUtil.readText(is);
        Map<String, String> pathsToUrls = Arrays.stream(data.split("\n"))
            .map(s -> s.split("->"))
            .filter(array -> array.length == 2)
            .collect(Collectors.toMap(array -> array[0].trim(), array -> array[1].trim()));

        if (pathsToUrls.size() > 0) {
          return new S3ArtifactsListBrowser(pathsToUrls);
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
