package jetbrains.buildServer.artifacts.s3.utils;

import com.intellij.openapi.util.io.StreamUtil;
import jetbrains.buildServer.artifacts.s3.S3Artifact;
import jetbrains.buildServer.util.StringUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by Nikita.Skvortsov
 * date: 18.02.2016.
 */
public class S3Util {
  public static Map<String, String> readArtifactsUrls(InputStream is) throws IOException {
    final String data = StreamUtil.readText(is);
    return Arrays.stream(data.split("\n"))
        .map(s -> s.split("->"))
        .filter(array -> array.length == 2)
        .collect(Collectors.toMap(array -> array[0].trim(), array -> array[1].trim()));
  }

  public static List<S3Artifact> readS3Artifacts(InputStream is) throws IOException {
    final String data = StreamUtil.readText(is);
    return Arrays.stream(data.split("\n"))
        .filter(StringUtil::isNotEmpty)
        .map(S3Artifact::new)
        .collect(Collectors.toList());
  }
}
