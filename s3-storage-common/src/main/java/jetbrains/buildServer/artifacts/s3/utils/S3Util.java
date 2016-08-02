package jetbrains.buildServer.artifacts.s3.utils;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.StreamUtil;
import jetbrains.buildServer.artifacts.ExternalArtifact;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static jetbrains.buildServer.artifacts.s3.S3Constants.*;

/**
 * Created by Nikita.Skvortsov
 * date: 18.02.2016.
 */
public class S3Util {

  private static final Logger LOG = Logger.getInstance(S3Util.class.getName());

  public static Map<String, String> readArtifactsUrls(InputStream is) throws IOException {
    final String data = StreamUtil.readText(is);
    final Map<String, String> result = new HashMap<String, String>();

    for (String s : data.split("\n")) {
      final String[] parts = s.split("->");
      result.put(parts[0].trim(), parts[1].trim());
    }

    return result;
  }

  public static List<ExternalArtifact> readExternalArtifacts(InputStream is) throws IOException {
    final Gson gson = new Gson();
    Type artifactsList = new TypeToken<List<ExternalArtifact>>() {}.getType();
    String json = StreamUtil.readText(is);
    return gson.fromJson(json, artifactsList);
  }

  public static void writeExternalArtifacts(@NotNull final List<ExternalArtifact> artifacts, @NotNull final File file) {
    final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    try {
      FileUtil.writeFile(file, gson.toJson(artifacts), "UTF-8");
    } catch (IOException e) {
      LOG.warnAndDebugDetails("Failed to write s3 artifact info to file [" + file.getAbsolutePath() + "]: " + e.getMessage(), e);
    }
  }

  public static AmazonS3 createAmazonClient(Map<String, String> params) {
    final String accessKeyId = params.get(S3_KEY_ID);
    final String secretAccessKey = params.get(S3_SECRET_KEY);
    final Region region = Region.getRegion(Regions.fromName(params.get(S3_REGION)));

    AmazonS3 s3client = new AmazonS3Client(new BasicAWSCredentials(accessKeyId, secretAccessKey));
    s3client.setRegion(region);
    return s3client;
  }
}
