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

  public S3Artifact(@NotNull String serialized) {
    String[] tmp = serialized.split("->");
    myPath = tmp[0].trim();
    final int i = myPath.lastIndexOf("/");
    if (i > -1) {
      myName = myPath.substring(i + 1);
    } else {
      myName = myPath;
    }

    String[] urlSize = tmp[1].trim().split(" ");
    myUrl = urlSize[0];
    mySize = Long.valueOf(urlSize[1]);
  }

  public S3Artifact(@NotNull final String path,
                    @Nullable final String url,
                    final long size) {
    myPath = path;
    myUrl = url;
    mySize = size;
    final int i = path.lastIndexOf("/");
    if (i > -1) {
      myName = path.substring(i + 1);
    } else {
      myName = path;
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
  public String getKey() {
    if (myUrl == null) {
      return "";
    }
    try {
      final URL url = new URL(myUrl);
      final String path = URLDecoder.decode(url.getPath(), "UTF-8");
      return path.substring(path.indexOf("/", 1) + 1);
    } catch (MalformedURLException e) {
      // should not happen
    } catch (UnsupportedEncodingException e) {
      // should not happen
    }
    return "";
  }

  @NotNull
  public String toSerialized() {
    return myPath + "->" + myUrl + " " + mySize;
  }
}
