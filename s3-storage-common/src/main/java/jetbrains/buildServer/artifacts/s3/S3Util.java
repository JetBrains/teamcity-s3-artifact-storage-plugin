package jetbrains.buildServer.artifacts.s3;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

import static jetbrains.buildServer.artifacts.s3.S3Constants.*;

/**
 * Created by Nikita.Skvortsov
 * date: 02.08.2016.
 */
public class S3Util {
  @NotNull
  public static AmazonS3 createAmazonClient(Map<String, String> params) {
    final String accessKeyId = params.get(S3_KEY_ID);
    final String secretAccessKey = params.get(S3_SECRET_KEY);
    final Region region = Region.getRegion(Regions.fromName(params.get(S3_REGION)));

    AmazonS3 s3client = new AmazonS3Client(new BasicAWSCredentials(accessKeyId, secretAccessKey));
    s3client.setRegion(region);
    return s3client;
  }

  @NotNull
  public static Map<String, String> validateParameters(@NotNull Map<String, String> params) {
    final Map<String, String> invalids = new HashMap<String, String>();
    if (StringUtil.isEmptyOrSpaces(params.get(S3Constants.S3_BUCKET_NAME))) {
      invalids.put(S3_BUCKET_NAME, "S3 Bucket Name mustn't be empty");
    }
    return invalids;
  }
}
