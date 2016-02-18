package jetbrains.buildServer.artifacts;

import jetbrains.buildServer.util.browser.Element;
import jetbrains.buildServer.web.util.lazytree.DefaultLazyTreeElementRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.servlet.http.HttpServletRequest;

/**
 * Created by Nikita.Skvortsov
 * date: 17.02.2016.
 */
public class S3ElementRenderer extends DefaultLazyTreeElementRenderer {
  @Override
  public void prepareForRequest(@NotNull HttpServletRequest httpServletRequest) {
    super.prepareForRequest(httpServletRequest);
  }

  @Override
  public boolean ignoreElement(@NotNull Element element) {
    return !(element instanceof S3Element);
  }

  @NotNull
  @Override
  public String getTextForElement(@NotNull Element element) {
    if (element instanceof S3Element) {
      return super.getTextForElement(element);
    }
    return "";
  }

  @Nullable
  @Override
  public String getOnClickForLeaf(@NotNull Element element) {
    if (element instanceof S3Element) {
      super.getOnClickForLeaf(element);
    }
    return null;
  }

  @Nullable
  @Override
  public String getOnClickForNonLeaf(@NotNull Element element) {
    if (element instanceof S3Element) {
      super.getOnClickForNonLeaf(element);
    }
    return null;
  }

  @Override
  public String getHrefForLeaf(@NotNull Element leaf) {
    if (leaf instanceof S3Element) {
      return ((S3Element)leaf).getUrl();
    }
    return null;
  }

  @Nullable
  @Override
  public String getCssClassForElement(@NotNull Element element) {
    if (element instanceof S3Element) {
      super.getCssClassForElement(element);
    }
    return null;
  }

  @Nullable
  @Override
  public String getCustomHtmlForElement(@NotNull Element element, @NotNull String s) {
    if (element instanceof S3Element) {
      super.getCustomHtmlForElement(element, s);
    }
    return null;
  }
}
