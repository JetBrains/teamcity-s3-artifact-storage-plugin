package jetbrains.buildServer.artifacts.s3.cloudfront;

public class CloudFrontUtils {

  public enum Protocol {
    http,
    https
  }

  public static String generateResourcePath(Protocol protocol, String distributionDomain, String resourcePath) {
    return protocol + "://" + distributionDomain + "/" + resourcePath;
  }
}
