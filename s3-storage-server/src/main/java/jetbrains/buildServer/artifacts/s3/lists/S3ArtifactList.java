package jetbrains.buildServer.artifacts.s3.lists;

import jetbrains.buildServer.artifacts.util.ExternalArtifactUtil;
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

import static jetbrains.buildServer.artifacts.s3.S3Constants.EXTERNAL_ARTIFACTS_LIST;

/**
 * @author vbedrosova
 */
public class S3ArtifactList extends SimplePageExtension {

  @NotNull
  private final SBuildServer myServer;

  protected S3ArtifactList(@NotNull String id,
                           @NotNull PlaceId placeId,
                           @NotNull PagePlaces pagePlaces,
                           @NotNull SBuildServer server,
                           @NotNull PluginDescriptor descriptor) {
    super(pagePlaces, placeId, id, descriptor.getPluginResourcesPath("s3_list.jsp"));
    myServer = server;
    register();
  }

  @Override
  public boolean isAvailable(@NotNull HttpServletRequest request) {
    final SBuild sBuild = BuildDataExtensionUtil.retrieveBuild(request, myServer);
    return sBuild != null && getExternalArtifactsListArtifact(sBuild).isAvailable();
  }

  @NotNull
  private BuildArtifactHolder getExternalArtifactsListArtifact(SBuild sBuild) {
    return sBuild.getArtifacts(BuildArtifactsViewMode.VIEW_ALL).findArtifact(EXTERNAL_ARTIFACTS_LIST);
  }

  @NotNull
  private SBuildServer getServer() {
    return myServer;
  }

  @Override
  public void fillModel(@NotNull Map<String, Object> model, @NotNull HttpServletRequest request) {
    super.fillModel(model, request);

    final SBuild sBuild = BuildDataExtensionUtil.retrieveBuild(request, getServer());
    if (sBuild == null) return;

    final BuildArtifactHolder artifact = sBuild.getArtifacts(BuildArtifactsViewMode.VIEW_ALL).findArtifact(EXTERNAL_ARTIFACTS_LIST);
    if (artifact.isAvailable()) {
      try {
        model.put("artifacts", ExternalArtifactUtil.readExternalArtifacts(artifact.getArtifact().getInputStream()));
      } catch (IOException e) {
        Loggers.SERVER.warnAndDebugDetails("Failed to read s3 artifacts list for build " + sBuild.getBuildDescription() + ". See debug logs for details", e);
      }
    }
  }
}