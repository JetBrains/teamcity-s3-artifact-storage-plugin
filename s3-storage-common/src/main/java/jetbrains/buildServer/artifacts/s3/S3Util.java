package jetbrains.buildServer.artifacts.s3;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;

import java.util.Map;

import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_KEY_ID;
import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_REGION;
import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_SECRET_KEY;

/**
 * Created by Nikita.Skvortsov
 * date: 02.08.2016.
 */
public class S3Util {
  public static AmazonS3 createAmazonClient(Map<String, String> params) {
    final String accessKeyId = params.get(S3_KEY_ID);
    final String secretAccessKey = params.get(S3_SECRET_KEY);
    final Region region = Region.getRegion(Regions.fromName(params.get(S3_REGION)));

    AmazonS3 s3client = new AmazonS3Client(new BasicAWSCredentials(accessKeyId, secretAccessKey));
    s3client.setRegion(region);
    return s3client;
  }
}
