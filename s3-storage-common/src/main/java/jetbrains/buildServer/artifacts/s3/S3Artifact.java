package jetbrains.buildServer.artifacts.s3;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;

/**
 * Created by Nikita.Skvortsov
 * date: 24.03.2016.
 */
public class S3Artifact {
  @NotNull private final String myPath;
  @Nullable private final String myUrl;
  @NotNull private final String myName;
  private final long mySize;
  @NotNull private final String myKey;


  public S3Artifact(@NotNull final String path,
                    @Nullable final String url,
                    final long size,
                    @NotNull final String key) {
    myPath = path;
    myUrl = url;
    mySize = size;
    final int i = path.lastIndexOf("/");
    if (i > -1) {
      myName = path.substring(i + 1);
    } else {
      myName = path;
    }
    myKey = key;
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
  public String getKey() {
    return myKey;
  }
}
