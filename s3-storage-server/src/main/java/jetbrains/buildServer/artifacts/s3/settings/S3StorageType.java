package jetbrains.buildServer.artifacts.s3.settings;

import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.serverSide.storage.StorageType;
import jetbrains.buildServer.serverSide.storage.StorageTypeRegistry;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Nikita.Skvortsov
 * date: 24.02.2016.
 */
public class S3StorageType extends StorageType {

  private final String mySettingsJSP;

  public S3StorageType(@NotNull StorageTypeRegistry registry,
                       @NotNull PluginDescriptor descriptor) {
    mySettingsJSP = descriptor.getPluginResourcesPath("s3_storage_settings.jsp");
    registry.registerStorageType(this);
  }

  @NotNull
  @Override
  public String getType() {
    return S3Constants.S3_STORAGE_TYPE;
  }

  @NotNull
  @Override
  public String getName() {
    return "S3 Storage";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Uses s3 bucket to store build artifacts";
  }

  @NotNull
  @Override
  public String getEditStorageParametersPath() {
    return mySettingsJSP;
  }

  @Nullable
  @Override
  public Map<String, String> getDefaultParameters() {
    Map<String, String> result = new HashMap<>();
    result.put(S3Constants.S3_PATH_PREFIX, "%system.teamcity.projectName%/%system.teamcity.buildConfName%/%teamcity.build.id%");
    return result;
  }
}
