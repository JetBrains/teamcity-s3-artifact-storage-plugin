package jetbrains.buildServer.artifacts;

import jetbrains.buildServer.util.browser.Browser;
import jetbrains.buildServer.util.browser.BrowserException;
import jetbrains.buildServer.util.browser.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;

import static org.aspectj.weaver.tools.cache.SimpleCacheFactory.path;

/**
 * Created by Nikita.Skvortsov
 * date: 08.02.2016.
 */
public class S3Element implements Element {

  @NotNull private final S3ArtifactsListBrowser myBrowser;
  @NotNull private final String myPath;
  @Nullable private final String myUrl;
  private final String myName;
  private final long mySize;

  public S3Element(@NotNull String path, @Nullable S3Artifact delegate, @NotNull S3ArtifactsListBrowser browser) {

    myPath = path;
    final int i = path.lastIndexOf("/");
    if (i > -1) {
      myName = path.substring(i + 1);
    } else {
      myName = path;
    }

    myUrl = delegate == null ? null : delegate.getUrl();
    mySize = delegate == null ? 0 : delegate.getSize();
    myBrowser = browser;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public String getFullName() {
    return myPath;
  }

  @Override
  public boolean isLeaf() {
    return myUrl != null;
  }

  @Nullable
  @Override
  public Iterable<Element> getChildren() throws BrowserException {
    return myBrowser.getChildren(myPath);
  }

  @Override
  public boolean isContentAvailable() {
    return false;
  }

  @NotNull
  @Override
  public InputStream getInputStream() throws IllegalStateException, IOException, BrowserException {
    throw new IllegalStateException("Can not provide an input stream for S3 artifact");
  }

  @Override
  public long getSize() {
    return mySize;
  }

  @NotNull
  @Override
  public Browser getBrowser() {
    return myBrowser;
  }

  public String getUrl() {
    return myUrl;
  }
}
