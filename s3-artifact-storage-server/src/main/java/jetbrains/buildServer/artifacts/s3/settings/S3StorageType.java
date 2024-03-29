

package jetbrains.buildServer.artifacts.s3.settings;

import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.ServerSettings;
import jetbrains.buildServer.serverSide.artifacts.ArtifactStorageType;
import jetbrains.buildServer.serverSide.artifacts.ArtifactStorageTypeRegistry;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    return "AWS S3";
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
    result.put(S3Constants.S3_USE_PRE_SIGNED_URL_FOR_UPLOAD, Boolean.toString(true));
    result.put(S3Constants.S3_VERIFY_INTEGRITY_AFTER_UPLOAD, Boolean.toString(true));
    result.put(AWSCommonParams.EXTERNAL_ID_PARAM, "TeamCity-server-" + myServerSettings.getServerUUID());
    // Ensures backwards compatibility with old S3 configurations and fixes TW-81457
    result.put(AWSCommonParams.SERVICE_ENDPOINT_PARAM, "");
    return result;
  }

  @Nullable
  @Override
  public PropertiesProcessor getParametersProcessor() {
    return new S3PropertiesProcessor();
  }

  @NotNull
  @Override
  public SettingsPreprocessor getSettingsPreprocessor() {
    return input -> input;
  }
}
