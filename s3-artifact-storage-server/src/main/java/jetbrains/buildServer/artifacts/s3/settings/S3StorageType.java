/*
 * Copyright 2000-2022 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.artifacts.s3.settings;

import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.artifacts.ArtifactStorageType;
import jetbrains.buildServer.serverSide.artifacts.ArtifactStorageTypeRegistry;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by Nikita.Skvortsov
 * date: 24.02.2016.
 */
public class S3StorageType extends ArtifactStorageType {

  @NotNull private final String mySettingsJSP;

  public S3StorageType(@NotNull ArtifactStorageTypeRegistry registry,
                       @NotNull PluginDescriptor descriptor) {
    mySettingsJSP = descriptor.getPluginResourcesPath(S3Constants.S3_SETTINGS_PATH + ".jsp");
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
