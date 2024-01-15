package jetbrains.buildServer.artifacts.s3;

import java.util.Map;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.Used;
import jetbrains.buildServer.artifacts.s3.amazonClient.AmazonS3Provider;
import org.jetbrains.annotations.NotNull;

public class CheckS3TransferAccelerationAvailability extends S3ClientResourceFetcher<CheckS3TransferAccelerationAvailability.S3AccelerationDto> {
  private final AmazonS3Provider myAmazonS3Builder;

  public CheckS3TransferAccelerationAvailability(AmazonS3Provider amazonS3Provider) {
    myAmazonS3Builder = amazonS3Provider;
  }

  @Override
  protected S3AccelerationDto fetchCurrentValue(Map<String, String> parameters, @NotNull String projectId) throws Exception{
    return fetchDto(parameters, projectId);
  }

  @Override
  protected S3AccelerationDto fetchDto(Map<String, String> parameters, @NotNull String projectId) throws Exception {
    return myAmazonS3Builder.withS3Client(
      parameters,
      projectId,
      s3Client -> {
        final String bucketName = S3Util.getBucketName(parameters);
        if (bucketName == null) {
          final String message = String.format("Invalid request: %s parameter was not set", S3Util.beanPropertyNameForBucketName());
          throw new IllegalArgumentException(message);
        }
        // Verify that transfer acceleration is enabled for the bucket.
        String accelerateStatus = s3Client.getBucketAccelerateConfiguration(bucketName).getStatus();

        return new S3AccelerationDto(bucketName, accelerateStatus);
      });
  }

  @XmlRootElement(name = "s3Acceleration")
  public static class S3AccelerationDto {
    private final String myBucketName;
    private final String myAccelerationStatus;

    @Used("xml-serialization")
    public S3AccelerationDto() {
      myBucketName = null;
      myAccelerationStatus = null;
    }

    public S3AccelerationDto(String bucketName, String accelerateStatus) {
      myBucketName = bucketName;
      myAccelerationStatus = accelerateStatus;
    }

    @XmlAttribute(name = "bucketName")
    public String getBucketName() {
      return myBucketName;
    }

    @XmlAttribute(name = "accelerationStatus")
    public String getAccelerationStatus() {
      return myAccelerationStatus;
    }
  }
}
