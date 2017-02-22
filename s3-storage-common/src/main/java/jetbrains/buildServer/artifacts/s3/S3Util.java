package jetbrains.buildServer.artifacts.s3;

import com.amazonaws.services.s3.AmazonS3;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_BUCKET_NAME;
import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_PATH_PREFIX;

/**
 * Created by Nikita.Skvortsov
 * date: 02.08.2016.
 */
public class S3Util {
  @NotNull
  public static AmazonS3 createAmazonClient(Map<String, String> params) {
    return AWSCommonParams.createAWSClients(params).createS3Client();
  }

  @NotNull
  public static Map<String, String> validateParameters(@NotNull Map<String, String> params, boolean acceptReferences) {
    final Map<String, String> invalids = new HashMap<String, String>();
    if (StringUtil.isEmptyOrSpaces(getBucketName(params))) {
      invalids.put(S3_BUCKET_NAME, "S3 bucket name mustn't be empty");
    }
    if (StringUtil.isEmptyOrSpaces(getPathPrefix(params))) {
      invalids.put(S3_PATH_PREFIX, "Path to build artifacts mustn't be empty");
    }
    invalids.putAll(AWSCommonParams.validate(params, acceptReferences));
    return invalids;
  }

  @NotNull
  public static String joinStrings(@NotNull Collection<String> strings) {
    if (strings.isEmpty()) return StringUtil.EMPTY;
    final StringBuilder sb = new StringBuilder();
    for (String s : strings) sb.append(s).append("\n");
    return sb.toString();
  }

  @Nullable
  public static String getBucketName(@NotNull Map<String, String> params) {
    return params.get(S3Constants.S3_BUCKET_NAME);
  }

  @Nullable
  public static String getPathPrefix(@NotNull Map<String, String> params) {
    String pathPrefix = params.get(S3_PATH_PREFIX);
    if (StringUtil.isEmptyOrSpaces(pathPrefix)) {
      return null;
    }
    pathPrefix = pathPrefix.endsWith("/") ? pathPrefix : pathPrefix + "/";
    pathPrefix = pathPrefix.startsWith("/") ? pathPrefix.substring(1) : pathPrefix;
    return pathPrefix;
  }
}
