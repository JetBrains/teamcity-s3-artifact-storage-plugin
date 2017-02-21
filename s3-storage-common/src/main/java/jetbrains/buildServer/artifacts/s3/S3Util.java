package jetbrains.buildServer.artifacts.s3;

import com.amazonaws.services.s3.AmazonS3;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_BUCKET_NAME;

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
  public static Map<String, String> validateParameters(@NotNull Map<String, String> params) {
    final Map<String, String> invalids = new HashMap<String, String>();
    if (StringUtil.isEmptyOrSpaces(params.get(S3Constants.S3_BUCKET_NAME))) {
      invalids.put(S3_BUCKET_NAME, "S3 Bucket Name mustn't be empty");
    }
    invalids.putAll(AWSCommonParams.validate(params, true));
    return invalids;
  }
}
