package jetbrains.buildServer.artifacts.s3;

public class PresignedUrlWithTtl {
  private final String url;
  private final int urlTtlSeconds;

  public PresignedUrlWithTtl(String url, int urlTtlSeconds) {
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
