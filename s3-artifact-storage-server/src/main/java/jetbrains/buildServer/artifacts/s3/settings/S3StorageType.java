/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.artifacts.s3.util.ParamUtil;
import jetbrains.buildServer.artifacts.s3.web.BucketLocationHandler;
import jetbrains.buildServer.artifacts.s3.web.S3StoragePropertiesUtil;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.artifacts.ArtifactStorageType;
import jetbrains.buildServer.serverSide.artifacts.ArtifactStorageTypeRegistry;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import jetbrains.buildServer.util.amazon.AWSRegions;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_USE_PRE_SIGNED_URL_FOR_UPLOAD;
import static jetbrains.buildServer.util.amazon.AWSCommonParams.REGION_NAME_PARAM;
import static jetbrains.buildServer.util.amazon.AWSCommonParams.SECURE_SECRET_ACCESS_KEY_PARAM;

/**
 * Created by Nikita.Skvortsov
 * date: 24.02.2016.
 */
public class S3StorageType extends ArtifactStorageType {

  @NotNull private final String mySettingsJSP;
  @NotNull private final ServerSettings myServerSettings;
  @NotNull private final ServerPaths myServerPaths;

  public S3StorageType(@NotNull ArtifactStorageTypeRegistry registry,
                       @NotNull PluginDescriptor descriptor,
                       @NotNull ServerSettings serverSettings,
                       @NotNull ServerPaths serverPaths) {
    mySettingsJSP = descriptor.getPluginResourcesPath(S3Constants.S3_SETTINGS_PATH + ".jsp");
    myServerSettings = serverSettings;
    myServerPaths = serverPaths;
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
    Map<String, String> result = new HashMap<>(AWSCommonParams.getDefaults(myServerSettings.getServerUUID()));
    result.put(AWSCommonParams.REGION_NAME_PARAM, AWSRegions.DEFAULT_REGION);
    result.put(S3Constants.S3_USE_PRE_SIGNED_URL_FOR_UPLOAD, Boolean.toString(true));
    return result;
  }

  @Nullable
  @Override
  public PropertiesProcessor getParametersProcessor() {
    return params -> {
      S3StoragePropertiesUtil.processProperties(params);
      final ArrayList<InvalidProperty> invalids = new ArrayList<>();
      for (Map.Entry<String, String> e : S3Util.validateParameters(params, true).entrySet()) {
        invalids.add(new InvalidProperty(e.getKey(), e.getValue()));
      }

      final String bucketName = S3Util.getBucketName(params);
      if (bucketName != null) {
        try {
          final String location = S3Util.withS3ClientShuttingDownImmediately(
            ParamUtil.putSslValues(myServerPaths, params),
            client -> S3Util.withClientCorrectingRegion(client, params, correctedClient -> correctedClient.getBucketLocation(bucketName))
          );
          if (location == null) {
            invalids.add(new InvalidProperty(S3Util.beanPropertyNameForBucketName(), "Bucket does not exist"));
          } else {
            if (TeamCityProperties.getBooleanOrTrue("teamcity.internal.storage.s3.autoCorrectRegion") && !location.equalsIgnoreCase(params.get(REGION_NAME_PARAM))) {
              params.put(REGION_NAME_PARAM, BucketLocationHandler.getRegionName(location));
            }
          }
        } catch (Throwable e) {
          invalids.add(new InvalidProperty(S3Util.beanPropertyNameForBucketName(), e.getMessage()));
        }
      }

      return invalids;
    };
  }

  @NotNull
  @Override
  public SettingsPreprocessor getSettingsPreprocessor() {
    return input -> {
      final Map<String, String> output = new HashMap<>(input);
      if (Boolean.parseBoolean(input.get(S3_USE_PRE_SIGNED_URL_FOR_UPLOAD))) {
        output.remove(SECURE_SECRET_ACCESS_KEY_PARAM);
      }
      return output;
    };
  }
}
