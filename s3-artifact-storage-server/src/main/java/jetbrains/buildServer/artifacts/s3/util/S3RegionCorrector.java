package jetbrains.buildServer.artifacts.s3.util;

import com.intellij.openapi.diagnostic.Logger;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.serverSide.IOGuard;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.artifacts.s3.BucketLocationFetcher.getRegionName;
import static jetbrains.buildServer.artifacts.s3.S3Util.*;
import static jetbrains.buildServer.util.amazon.AWSCommonParams.REGION_NAME_PARAM;

public final class S3RegionCorrector {
  private static final Logger LOGGER = Logger.getInstance(S3RegionCorrector.class.getName());

  private S3RegionCorrector() {
  }

  public static Map<String, String> correctRegion(@NotNull final String bucketName, @NotNull final Map<String, String> storageSettings) {
    if (TeamCityProperties.getBooleanOrTrue("teamcity.internal.storage.s3.autoCorrectRegion")) {
      final String initialRegion = storageSettings.get(REGION_NAME_PARAM);
      final String correctedRegion = IOGuard.allowNetworkCall(() -> {
        try {
          return withCorrectingRegionAndAcceleration(storageSettings, client -> getRegionName(client.getBucketLocation(bucketName)));
        } catch (Throwable t) {
          throw new RuntimeException(t);
        }
      });
      if (!correctedRegion.equalsIgnoreCase(initialRegion)) {
        final HashMap<String, String> correctedSettings = new HashMap<>(storageSettings);
        correctedSettings.put(REGION_NAME_PARAM, correctedRegion);
        LOGGER.debug(() -> "Bucket [" + bucketName + "] location is corrected: [" + initialRegion + "] -> [" + correctedRegion + "]");
        return correctedSettings;
      }
    }
    return storageSettings;
  }
}
