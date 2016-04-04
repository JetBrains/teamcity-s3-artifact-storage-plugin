package jetbrains.buildServer.artifacts.s3.tree;

import jetbrains.buildServer.artifacts.S3Artifact;
import jetbrains.buildServer.util.browser.Behaviour;
import jetbrains.buildServer.util.browser.Browser;
import jetbrains.buildServer.util.browser.BrowserException;
import jetbrains.buildServer.util.browser.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by Nikita.Skvortsov
 * date: 08.02.2016.
 */
public class S3ArtifactsListBrowser implements Browser {

  private final Map<String, S3Artifact> myPathToArtifact;

  public S3ArtifactsListBrowser(List<S3Artifact> artifacts) {
    myPathToArtifact = artifacts.stream().collect(Collectors.toMap(S3Artifact::getPath, Function.identity()));
  }

  @NotNull
  @Override
  public Element getRoot() throws BrowserException {
    return new S3Element("", new S3Artifact("", null, 0), this);
  }

  @Nullable
  @Override
  public Element getElement(@NotNull String prefix) throws BrowserException {
    final String path = myPathToArtifact.keySet().stream()
        .filter(k -> k.startsWith(prefix) && (k.length() == prefix.length() || k.charAt(prefix.length()) == '/'))
        .findFirst().orElse(null);
    if (path == null) {
      return null;
    } else {
      return new S3Element(prefix, myPathToArtifact.get(prefix), this);
    }
  }

  @NotNull
  @Override
  public Behaviour getBehaviour() {
    return Behaviour.DEFAULT_BEHAVIOUR;
  }

  public Iterable<Element> getChildren(String path) {
    return myPathToArtifact.keySet().stream()
        .filter(k -> k.startsWith(path) && (path.length() == 0 || k.length() == path.length() || k.charAt(path.length()) == '/'))
        .collect(Collectors.toSet()).stream()
        .map(k -> k.indexOf("/", path.length() + 1) > -1 ? k.substring(0, k.indexOf("/", path.length() + 1)) : k)
        .distinct()
        .map(k -> new S3Element(k, myPathToArtifact.get(k), this))
        .collect(Collectors.toSet());
  }
}
