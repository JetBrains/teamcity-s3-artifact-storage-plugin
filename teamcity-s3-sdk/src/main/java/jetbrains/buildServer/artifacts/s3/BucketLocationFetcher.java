

package jetbrains.buildServer.artifacts.s3;

import java.util.Map;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.Used;
import jetbrains.buildServer.artifacts.s3.amazonClient.AmazonS3Provider;
import jetbrains.buildServer.serverSide.connections.credentials.ConnectionCredentialsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.amazon.awssdk.regions.Region;

public class BucketLocationFetcher extends S3ClientResourceFetcher<BucketLocationFetcher.BucketLocationDto> {

  private final AmazonS3Provider myAmazonS3Builder;

  public BucketLocationFetcher(@NotNull AmazonS3Provider amazonS3Provider) {
    myAmazonS3Builder = amazonS3Provider;
  }

  public static String getRegionName(@Nullable String location) {
    if (location == null) {
      return Region.US_EAST_1.id();
    }

    if ("EU".equalsIgnoreCase(location)) {
      return Region.EU_WEST_1.id();
    }

    return location;
  }

  @Override
  protected BucketLocationDto fetchCurrentValue(Map<String, String> parameters, @NotNull String projectId) throws Exception{
    return fetchDto(parameters, projectId);
  }

  @Override
  protected BucketLocationDto fetchDto(final Map<String, String> parameters, @NotNull String projectId) throws ConnectionCredentialsException {
    return myAmazonS3Builder.withS3Client(
      parameters,
      projectId,
      s3Client -> {
        final String bucketName = S3Util.getBucketName(parameters);
        if (bucketName == null) {
          final String message = String.format("Invalid request: %s parameter was not set", S3Util.beanPropertyNameForBucketName());
          throw new IllegalArgumentException(message);
        }
        return new BucketLocationDto(bucketName, getRegionName(s3Client.headBucket(b -> b.bucket(bucketName)).bucketRegion()));
      });
  }

  @XmlRootElement(name = "bucket")
  public static class BucketLocationDto implements S3Dto {
    private final String name;
    private final String location;

    @Used("xml-serialization")
    public BucketLocationDto() {
      name = null;
      location = null;
    }

    public BucketLocationDto(String name, String location) {
      this.name = name;
      this.location = location;
    }

    @XmlAttribute(name = "name")
    public String getName() {
      return name;
    }

    @XmlAttribute(name = "location")
    public String getLocation() {
      return location;
    }
  }
}
