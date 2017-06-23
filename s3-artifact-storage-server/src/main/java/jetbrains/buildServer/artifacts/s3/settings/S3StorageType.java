package jetbrains.buildServer.artifacts.s3.settings;

import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.ServerSettings;
import jetbrains.buildServer.serverSide.artifacts.ArtifactStorageType;
import jetbrains.buildServer.serverSide.artifacts.ArtifactStorageTypeRegistry;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Nikita.Skvortsov
 * date: 24.02.2016.
 */
public class S3StorageType extends ArtifactStorageType {

  @NotNull private final String mySettingsJSP;
  @NotNull private final ServerSettings myServerSettings;

  public S3StorageType(@NotNull ArtifactStorageTypeRegistry registry,
                       @NotNull PluginDescriptor descriptor,
                       @NotNull ServerSettings serverSettings) {
    mySettingsJSP = descriptor.getPluginResourcesPath(S3Constants.S3_SETTINGS_PATH + ".jsp");
    myServerSettings = serverSettings;
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
    result.putAll(AWSCommonParams.getDefaults(myServerSettings.getServerUUID()));
    return result;
  }

  @Nullable
  @Override
  public PropertiesProcessor getParametersProcessor() {
    return params -> {
      final ArrayList<InvalidProperty> invalids = new ArrayList<>();
      for (Map.Entry<String, String> e : S3Util.validateParameters(params, true).entrySet()) {
        invalids.add(new InvalidProperty(e.getKey(), e.getValue()));
      }
      return invalids;
    };
  }
}
