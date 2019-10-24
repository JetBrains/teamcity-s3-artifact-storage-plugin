package jetbrains.buildServer.artifacts.s3.web;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import java.util.Map;
import jetbrains.buildServer.artifacts.s3.S3Util;
import org.jdom.Content;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

public class BucketLocationHandler extends S3ClientResourceHandler {
  @Override
  protected Content getContent(final AmazonS3 s3Client, final Map<String, String> parameters) {
    final String bucketName = S3Util.getBucketName(parameters);
    if (bucketName == null) {
      final String message = String.format("Invalid request: %s parameter was not set", S3Util.beanPropertyNameForBucketName());
      throw new IllegalArgumentException(message);
    }

    final String location = s3Client.getBucketLocation(bucketName);
    final String regionName = getRegionName(location);

    final Element bucketElement = new Element("bucket");
    bucketElement.setAttribute("name", bucketName);
    bucketElement.setAttribute("location", regionName);

    return bucketElement;
  }

  private static String getRegionName(@Nullable String location) {
    if (location == null) {
      return Regions.US_EAST_1.getName();
    }

    final Region region = RegionUtils.getRegion(location);
    if (region == null && location.equals("US")) {
      return Regions.US_EAST_1.getName();
    }

    return region != null ? region.getName() : location;
  }
}
