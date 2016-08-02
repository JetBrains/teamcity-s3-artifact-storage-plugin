package jetbrains.buildServer.artifacts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Nikita.Skvortsov
 * date: 02.08.2016.
 */
public class ExternalArtifact {
  @NotNull
  protected final String myPath;
  @Nullable
  protected final String myUrl;
  @NotNull
  protected final String myName;
  protected final long mySize;
  @NotNull
  private final Map<String, String> myProperties;


  public ExternalArtifact(@Nullable final String url,
                          @NotNull final String path,
                          final long size, String... properties) {
    myUrl = url;
    myPath = path;
    mySize = size;
    myProperties = new HashMap<String, String>();
    final int i = path.lastIndexOf("/");
    if (i > -1) {
      myName = path.substring(i + 1);
    } else {
      myName = path;
    }

    if (properties.length % 2 != 0) {
      throw new IllegalArgumentException("Expected even number of objects in format \"key1\", \"value1\", \"key2\", \"value2\"... " +
          "as properties, but got " + properties.length + " objects: [" + Arrays.toString(properties) + "]");
    }

    for (int j = 0; j < properties.length - 1; j = j + 2) {
      myProperties.put(properties[j], properties[j + 1]);
    }
  }

  @NotNull
  public String getPath() {
    return myPath;
  }

  @Nullable
  public String getUrl() {
    return myUrl;
  }

  public long getSize() {
    return mySize;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public Map<String, String> getProperties() {
    return myProperties;
  }
}
