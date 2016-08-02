package jetbrains.buildServer.artifacts.tree;

import jetbrains.buildServer.util.browser.Element;
import jetbrains.buildServer.web.util.lazytree.DefaultLazyTreeElementRenderer;
import org.jetbrains.annotations.NotNull;

import javax.servlet.http.HttpServletRequest;

/**
 * Created by Nikita.Skvortsov
 * date: 17.02.2016.
 */
public class ExternalArtifactElementRenderer extends DefaultLazyTreeElementRenderer {
  @Override
  public void prepareForRequest(@NotNull HttpServletRequest httpServletRequest) {
    super.prepareForRequest(httpServletRequest);
  }

  @Override
  public boolean ignoreElement(@NotNull Element element) {
    return !(element instanceof ExternalArtifactElement);
  }

  @Override
  public String getHrefForLeaf(@NotNull Element leaf) {
    if (leaf instanceof ExternalArtifactElement) {
      return ((ExternalArtifactElement)leaf).getUrl();
    }
    return null;
  }
}
