package jetbrains.buildServer.artifacts.s3.lists;

import jetbrains.buildServer.artifacts.ExternalArtifact;
import jetbrains.buildServer.artifacts.s3.utils.S3Util;
import jetbrains.buildServer.controllers.BuildDataExtensionUtil;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactHolder;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactsViewMode;
import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PlaceId;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.SimplePageExtension;
import org.jetbrains.annotations.NotNull;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

import static jetbrains.buildServer.artifacts.s3.S3Constants.*;
import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_ARTIFACTS_LIST_PATH;

/**
 * Created by Nikita.Skvortsov
 * date: 18.02.2016.
 */
public class S3ContentList extends SimplePageExtension {

  private final SBuildServer myServer;

  public S3ContentList(@NotNull PagePlaces pagePlaces,
                       @NotNull SBuildServer server,
                       @NotNull PluginDescriptor descriptor) {
    super(pagePlaces, PlaceId.BUILD_ARTIFACTS_FRAGMENT, "s3_list", descriptor.getPluginResourcesPath("s3_list.jsp"));
    myServer = server;
    register();
  }

  @Override
  public boolean isAvailable(@NotNull HttpServletRequest request) {
    final SBuild sBuild = BuildDataExtensionUtil.retrieveBuild(request, myServer);
    return sBuild != null && sBuild.getArtifacts(BuildArtifactsViewMode.VIEW_ALL).findArtifact(S3_ARTIFACTS_LIST_PATH + "/" + S3_ARTIFACTS_LIST).isAvailable();
  }

  @Override
  public void fillModel(@NotNull Map<String, Object> model, @NotNull HttpServletRequest request) {
    super.fillModel(model, request);
    final SBuild sBuild = BuildDataExtensionUtil.retrieveBuild(request, myServer);
    if (sBuild == null) {
      return;
    }
    final BuildArtifactHolder artifact = sBuild.getArtifacts(BuildArtifactsViewMode.VIEW_ALL).findArtifact(S3_ARTIFACTS_LIST_PATH + "/" + S3_ARTIFACTS_LIST);
    if (!artifact.isAvailable()) {
      return;
    } else {
      try {
        final Map<String, String> pathsWithUrls = S3Util.readExternalArtifacts(artifact.getArtifact().getInputStream())
            .stream().collect(Collectors.toMap(ExternalArtifact::getPath, ExternalArtifact::getUrl));
        model.put("pathsWithUrls", pathsWithUrls);
      } catch (IOException e) {
        Loggers.SERVER.warnAndDebugDetails("Failed to read s3 artifacts list for build " + sBuild.getBuildDescription() + ". See debug logs for details", e);
      }
    }


  }
}
