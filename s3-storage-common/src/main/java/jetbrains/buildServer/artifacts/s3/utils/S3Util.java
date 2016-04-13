package jetbrains.buildServer.artifacts.s3.utils;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.intellij.openapi.util.io.StreamUtil;
import jetbrains.buildServer.artifacts.s3.S3Artifact;
import jetbrains.buildServer.util.StringUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static jetbrains.buildServer.artifacts.s3.S3Constants.*;

/**
 * Created by Nikita.Skvortsov
 * date: 18.02.2016.
 */
public class S3Util {
  public static Map<String, String> readArtifactsUrls(InputStream is) throws IOException {
    final String data = StreamUtil.readText(is);
    final Map<String, String> result = new HashMap<String, String>();

    for (String s : data.split("\n")) {
      final String[] parts = s.split("->");
      result.put(parts[0].trim(), parts[1].trim());
    }

    return result;
  }

  public static List<S3Artifact> readS3Artifacts(InputStream is) throws IOException {
    final List<S3Artifact> result = new ArrayList<S3Artifact>();
    StringUtil.processLines(is, new StringUtil.LineProcessor() {
      @Override
      public boolean processLine(String s) {
        if (StringUtil.isNotEmpty(s)) {
          result.add(new S3Artifact(s));
        }
        return false;
      }
    });
    return result;
  }

  public static AmazonS3 createAmazonClient(Map<String, String> params) {
    final String accessKeyId = params.get(S3_KEY_ID);
    final String secretAccessKey = params.get(S3_SECRET_KEY);
    params.get(S3_BUCKET_NAME);
    Region usWest2 = Region.getRegion(Regions.US_WEST_2);

    AmazonS3 s3client = new AmazonS3Client(new BasicAWSCredentials(accessKeyId, secretAccessKey));
    s3client.setRegion(usWest2);
    return s3client;
  }
}
