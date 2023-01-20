package jetbrains.buildServer.artifacts.s3.cloudfront;

import org.jetbrains.annotations.Nullable;

public class RequestMetadata {
  private final String region;
  private final String userAgent;

  public RequestMetadata(@Nullable String region, @Nullable String userAgent) {
    this.region = region;
    this.userAgent = userAgent;
  }

  @Nullable
  public String getRegion() {
    return region;
  }

  @Nullable
  public String getUserAgent() {
    return userAgent;
  }

  public static RequestMetadata from(@Nullable String region, @Nullable String userAgent) {
    return new RequestMetadata(region, userAgent);
  }
}
