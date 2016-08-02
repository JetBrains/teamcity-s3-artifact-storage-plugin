package jetbrains.buildServer.artifacts.tree;

import jetbrains.buildServer.artifacts.ExternalArtifact;
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
public class ExternalArtifactsListBrowser implements Browser {

  private final Map<String, ExternalArtifact> myPathToArtifact;

  public ExternalArtifactsListBrowser(List<ExternalArtifact> artifacts) {
    myPathToArtifact = artifacts.stream().collect(Collectors.toMap(ExternalArtifact::getPath, Function.identity()));
  }

  @NotNull
  @Override
  public Element getRoot() throws BrowserException {
    return new ExternalArtifactElement("", new ExternalArtifact(null, "", 0), this);
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
      return new ExternalArtifactElement(prefix, myPathToArtifact.get(prefix), this);
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
        .map(k -> new ExternalArtifactElement(k, myPathToArtifact.get(k), this))
        .collect(Collectors.toSet());
  }
}
