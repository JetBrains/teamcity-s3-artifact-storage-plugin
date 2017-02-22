package jetbrains.buildServer.artifacts.s3.lists;

import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PlaceId;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * Created by Nikita.Skvortsov
 * date: 18.02.2016.
 */
public class S3PopupList extends S3ArtifactList {

  public S3PopupList(@NotNull PagePlaces pagePlaces,
                     @NotNull SBuildServer server,
                     @NotNull PluginDescriptor descriptor) {
    super("s3_popup", PlaceId.BUILD_ARTIFACTS_POPUP_FRAGMENT, pagePlaces, server, descriptor);
  }
}
