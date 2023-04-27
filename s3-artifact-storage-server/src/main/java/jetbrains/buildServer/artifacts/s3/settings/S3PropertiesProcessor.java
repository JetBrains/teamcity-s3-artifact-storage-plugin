package jetbrains.buildServer.artifacts.s3.settings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.artifacts.s3.cloudfront.CloudFrontConstants;
import jetbrains.buildServer.artifacts.s3.web.S3StoragePropertiesUtil;
import jetbrains.buildServer.clouds.amazon.connector.featureDevelopment.ChosenAwsConnPropertiesProcessor;
import jetbrains.buildServer.clouds.amazon.connector.utils.parameters.ParamUtil;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.util.amazon.AWSCommonParams;

import static jetbrains.buildServer.artifacts.ArtifactStorageSettings.TEAMCITY_STORAGE_TYPE_KEY;
import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_COMPATIBLE_STORAGE_TYPE;
import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_STORAGE_TYPE;

public class S3PropertiesProcessor implements PropertiesProcessor {

  @Override
  public Collection<InvalidProperty> process(Map<String, String> params) {
    final ArrayList<InvalidProperty> invalids = new ArrayList<>();

    if (ParamUtil.withAwsConnectionId(params)) {
      invalids.addAll(new ChosenAwsConnPropertiesProcessor().process(params));
    } else {
      S3StoragePropertiesUtil.processProperties(params);
      Map<String, String> awsErrors = AWSCommonParams.validate(params, true);
      for (Map.Entry<String, String> e : awsErrors.entrySet()) {
        invalids.add(new InvalidProperty(e.getKey(), e.getValue()));
      }
    }

    for (Map.Entry<String, String> e : S3Util.validateParameters(params, true).entrySet()) {
      invalids.add(new InvalidProperty(e.getKey(), e.getValue()));
    }

    final String bucketName = S3Util.getBucketName(params);
    if (bucketName != null) {
      if (CloudFrontConstants.isEnabled() && S3Util.getCloudFrontEnabled(params)) {
        invalids.addAll(new CloudFrontPropertiesProcessor().process(params));
      }
    }

    return invalids;
  }
}
