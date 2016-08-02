package jetbrains.buildServer.artifacts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Nikita.Skvortsov
 * date: 02.08.2016.
 *
 * @since 10.0
 *
 * Information about external artifact.
 */
public class ExternalArtifact {
  @NotNull   private final String myPath;
  @Nullable  private final String myUrl;
  @NotNull   private final String myName;
  @NotNull   private final Map<String, String> myProperties;
  private final long mySize;

  /**
   * Create new instance
   * @param url - url to artifact location at external storage, will be rendered to artifacts tree in Web UI
   * @param path - relative path to artifact inside build artifacts, including the artifact file name
   * @param size - size (bytes) of artifact
   * @param properties - additional properties map. Content of map will be copied.
   */
  public ExternalArtifact(@Nullable final String url,
                          @NotNull final String path,
                          final long size,
                          @NotNull  Map<String, String> properties) {
    this(url, path, size);
    getProperties().putAll(properties);
  }

  /**
   * Create new instance
   * @param url - url to artifact location at external storage, will be rendered to artifacts tree in Web UI
   * @param path - relative path to artifact inside build artifacts, including the artifact file name
   * @param size - size (bytes) of artifact
   * @param properties - additional properties in format "key1", "value1", "key2", "value2"...
   */
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
