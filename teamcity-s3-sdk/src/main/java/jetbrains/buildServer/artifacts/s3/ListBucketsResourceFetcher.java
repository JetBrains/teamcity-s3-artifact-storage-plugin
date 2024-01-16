

package jetbrains.buildServer.artifacts.s3;

import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.ListBucketsRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.*;
import jetbrains.buildServer.Used;
import jetbrains.buildServer.artifacts.s3.amazonClient.AmazonS3Provider;
import jetbrains.buildServer.serverSide.connections.credentials.ConnectionCredentialsException;
import org.jetbrains.annotations.NotNull;

/**
 * Gets a list of buckets in S3 storage.
 */
public class ListBucketsResourceFetcher extends S3ClientResourceFetcher<ListBucketsResourceFetcher.ListBucketsDto> {

  private final AmazonS3Provider myAmazonS3Provider;

  public ListBucketsResourceFetcher(@NotNull AmazonS3Provider amazonS3Provider) {
    myAmazonS3Provider = amazonS3Provider;
  }

  @Override
  protected ListBucketsDto fetchCurrentValue(Map<String, String> parameters, @NotNull String projectId){
    final String bucketName = S3Util.getBucketName(parameters);
    final ArrayList<BucketDto> buckets = new ArrayList<>();
    if (bucketName != null) {
      buckets.add(new BucketDto(bucketName));
    }
    return new ListBucketsDto(buckets);
  }

  @Override
  public ListBucketsDto fetchDto(final Map<String, String> parameters, @NotNull final String projectId) throws ConnectionCredentialsException {
    return myAmazonS3Provider.withS3Client(
      parameters,
      projectId,
      s3Client -> {
        List<BucketDto> bucketList = s3Client.listBuckets()
                                             .stream()
                                             .map(Bucket::getName)
                                             .map(BucketDto::new)
                                             .collect(Collectors.toList());
        return new ListBucketsDto(bucketList);
      });
  }

  @NotNull
  private Map<String, String> copyMap(final Map<String, String> parameters) {
    return new HashMap<>(parameters);
  }

  @XmlRootElement(name = "buckets")
  @XmlAccessorType(XmlAccessType.PROPERTY)
  public static class ListBucketsDto implements S3Dto {
    @NotNull
    private final List<BucketDto> buckets;

    @Used("xml-serialization")
    public ListBucketsDto() {
      buckets = null;
    }

    public ListBucketsDto(@NotNull final List<BucketDto> buckets) {
      this.buckets = buckets;
    }

    @XmlElement(name = "bucket")
    @XmlList
    @NotNull
    public List<BucketDto> getBuckets() {
      return buckets;
    }
  }

  @XmlRootElement(name = "bucket")
  public static class BucketDto {
    @NotNull
    private final String name;

    @Used("xml-serialization")
    public BucketDto() {
      name = null;
    }

    public BucketDto(@NotNull String name) {
      this.name = name;
    }

    @NotNull
    @XmlValue
    public String getName() {
      return name;
    }
  }
}
