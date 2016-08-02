package jetbrains.buildServer.artifacts.utils;

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

/**
 * Created by Nikita.Skvortsov
 * date: 18.02.2016.
 */
public class ExternalArtifactUtil {

  public static final String EXTERNAL_ARTIFACTS_LIST_NAME = "external_artifacts_list.txt";
  private static final Logger LOG = Logger.getInstance(ExternalArtifactUtil.class.getName());

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
      LOG.warnAndDebugDetails("Failed to write external artifact info to file [" + file.getAbsolutePath() + "]: " + e.getMessage(), e);
    }
  }

}
