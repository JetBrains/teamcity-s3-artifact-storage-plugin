package jetbrains.buildServer.artifacts;

import com.intellij.openapi.util.io.StreamUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by Nikita.Skvortsov
 * date: 18.02.2016.
 */
public class S3Util {
  static Map<String, String> readArtifactsUrls(InputStream is) throws IOException {
    final String data = StreamUtil.readText(is);
    return Arrays.stream(data.split("\n"))
        .map(s -> s.split("->"))
        .filter(array -> array.length == 2)
        .collect(Collectors.toMap(array -> array[0].trim(), array -> array[1].trim()));
  }
}
