package jetbrains.buildServer.artifacts;

import jetbrains.buildServer.util.browser.Behaviour;
import jetbrains.buildServer.util.browser.Browser;
import jetbrains.buildServer.util.browser.BrowserException;
import jetbrains.buildServer.util.browser.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by Nikita.Skvortsov
 * date: 08.02.2016.
 */
public class S3ArtifactsListBrowser implements Browser {

  private final Map<String, String> myPathsToUrls;

  public S3ArtifactsListBrowser(Map<String, String> pathsToUrls) {
    myPathsToUrls = Collections.unmodifiableMap(pathsToUrls);
  }

  @NotNull
  @Override
  public Element getRoot() throws BrowserException {
    return new S3Element("", null, this);
  }

  @Nullable
  @Override
  public Element getElement(@NotNull String s) throws BrowserException {
    final String key = myPathsToUrls.keySet().stream()
        .filter(k -> k.startsWith(s) && (k.length() == s.length() || k.charAt(s.length()) == '/'))
        .findFirst().orElse(null);
    if (key == null) {
      return null;
    } else {
      return new S3Element(key, myPathsToUrls.get(key), this);
    }
  }

  @NotNull
  @Override
  public Behaviour getBehaviour() {
    return Behaviour.DEFAULT_BEHAVIOUR;
  }

  public Iterable<Element> getChildren(String path) {
    return myPathsToUrls.keySet().stream()
        .filter(k -> k.startsWith(path) && (path.length() == 0 || k.length() == path.length() || k.charAt(path.length()) == '/'))
        .collect(Collectors.toSet()).stream()
        .map(k -> k.indexOf("/", path.length() + 1) > -1 ? k.substring(0, k.indexOf("/", path.length() + 1)) : k)
        .distinct()
        .map(k -> new S3Element(k, myPathsToUrls.get(k), this))
        .collect(Collectors.toSet());
  }
}
