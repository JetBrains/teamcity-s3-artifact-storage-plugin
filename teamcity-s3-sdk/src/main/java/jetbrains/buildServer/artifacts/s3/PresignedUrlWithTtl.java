package jetbrains.buildServer.artifacts.s3;

import org.jetbrains.annotations.NotNull;

public class PresignedUrlWithTtl {
  private final String url;
  private final int urlTtlSeconds;

  public PresignedUrlWithTtl(@NotNull String url, int urlTtlSeconds) {
    this.url = url;
    this.urlTtlSeconds = urlTtlSeconds;
  }

  public String getUrl() {
    return url;
  }

  public int getUrlTtlSeconds() {
    return urlTtlSeconds;
  }
}
