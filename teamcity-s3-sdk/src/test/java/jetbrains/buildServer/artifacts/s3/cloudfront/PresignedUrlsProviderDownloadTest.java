package jetbrains.buildServer.artifacts.s3.cloudfront;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.MockTimeService;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.S3PresignedUrlProvider;
import jetbrains.buildServer.artifacts.s3.S3PresignedUrlProviderImpl;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.artifacts.s3.amazonClient.AmazonS3Provider;
import jetbrains.buildServer.artifacts.s3.amazonClient.WithCloudFrontClient;
import jetbrains.buildServer.artifacts.s3.amazonClient.WithS3Client;
import jetbrains.buildServer.artifacts.s3.amazonClient.WithS3Presigner;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.connections.credentials.ConnectionCredentialsException;
import jetbrains.buildServer.util.TimeService;
import org.junit.Assert;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import static jetbrains.buildServer.artifacts.s3.S3Constants.*;
import static jetbrains.buildServer.artifacts.s3.cloudfront.CloudFrontConstants.*;

public class PresignedUrlsProviderDownloadTest extends BaseTestCase {

  private static final String PROJECT_ID = "projectId";
  private static final String OBJECT_KEY = "objectKey";
  private static final String BUCKET_NAME = "bucketName";
  private static final String REGION = "test-region-1";
  private static final String USER_AGENT = "Chrome";
  private static final String DOMAIN = "bucket.s3.us-east-1.amazonaws.com";
  private static final String PRESIGNED_URL = "http://presignedUrlRoot/" + BUCKET_NAME + "/" + OBJECT_KEY + "?otherInformation";
  private static final String RANDOM_PRIVATE_KEY = "-----BEGIN RSA PRIVATE KEY-----\n" +
                                                   "MIICWwIBAAKBgQCLRoFpZY0xT9rEMb7HmdyNCmmpIy9L/WuVzQPFHEAd7+DxuUSZ\n" +
                                                   "s+XuU6jNhbgQQZ8xY0dlTLIfUEWm1MoAcmTx1jarVImokKtfxxIA7wkg5t3j4YDd\n" +
                                                   "3bOloajIEXKIDUyb2jsUNdcQ555EhSWJ7iLOkMtVxdTgj/xXtXjP5T39owIDAQAB\n" +
                                                   "AoGAUBnQvAlH1yCSNVxW2P1It2VRt5Eg1pd9lL5AeNDyn0UjHmLrbWrlxtgesXVC\n" +
                                                   "kzbqk8EBguVFXHUSWgzv0Kn6G6xcTELdzsj+u7W0GYn1FzwV/5AdAn8rEgKHY9CT\n" +
                                                   "+wPIBmmLyWfUhE53hCzXKi/a8xKn38Cej/vEYZDr8Fm7sWECQQDzURLZZMX9QPl8\n" +
                                                   "lWVjfQxFWXRsmOu9oLfNrFrtahVaB0sK/XQWPkg1eHqzjOpKhajBcMXxzfLFjBHf\n" +
                                                   "JbfuswmNAkEAkokPBNC1bnUZS4wuBMc3XskQI3QqxLnWq6H4NjWhZydcssERvaUJ\n" +
                                                   "HfoVv+oiBd8JTOAFDY1gh+AjyZKhQbAf7wJADgbEcWfPyhOuzjHGkH+q72yCu6ft\n" +
                                                   "HxdewSqIa1tMM85r0wxdXtLkUsDJQgSDlRwzaENaBeB7eEvk09pWyhnkGQJANr6F\n" +
                                                   "oeLnJVESFitUtexHP14WbWhk4gIbtRtZw8bdedA2zYFbw2H4uf5ISd6Kv27H5rR6\n" +
                                                   "rsl2msyeEhx5qjTGeQJAe2+M6MWPXmROgyVxP4KsMIgtUq/5lpfPyMU4OOpDcwk5\n" +
                                                   "WKaUYRAGw6K0eaaMRrBQyJspN8JVZ6GXYK9nlbWu/A==\n" +
                                                   "-----END RSA PRIVATE KEY-----";
  private static final int FILE_SIZE_TRESHOLD_IN_GB = 1;

  private AmazonS3Provider myAmazonS3Provider;
  private S3Presigner myS3Presigner;
  private CloudFrontSettings myS3Settings;

  @BeforeMethod(alwaysRun = true)
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setInternalProperty(S3_URL_LIFETIME_SEC, "600");
    setInternalProperty(S3_URL_LIFETIME_EXTENDED_SEC, "10800");
    setInternalProperty(S3Constants.S3_DOWNLOAD_THRESHOLD_FOR_PRESIGN_URL_EXTENSION_IN_GB, FILE_SIZE_TRESHOLD_IN_GB);
    myAmazonS3Provider = Mockito.mock(AmazonS3Provider.class);
    myS3Presigner = Mockito.mock(S3Presigner.class);
    myS3Settings = Mockito.spy(new CloudFrontSettingsImpl(
      new HashMap<String, String>(){{
        put(S3_CLOUDFRONT_PUBLIC_KEY_ID, "publicKeyId");
        put(S3_CLOUDFRONT_DOWNLOAD_DISTRIBUTION, "cloudFrontDistribution");
        put(S3_CLOUDFRONT_PRIVATE_KEY, RANDOM_PRIVATE_KEY);
        put(S3_BUCKET_NAME, BUCKET_NAME);
      }},
      Collections.singletonMap(PROJECT_ID_PARAM, PROJECT_ID),
      RequestMetadata.from(REGION, USER_AGENT))
    );
  }

  @Test
  public void testGetPresignedUrlForLargeFile() throws IOException, ConnectionCredentialsException {
    testGetPresignedUrlForFile((long)Math.pow(2, 30) * FILE_SIZE_TRESHOLD_IN_GB + 1);
  }

  @Test
  public void testGetPresignedUrlForNormalFile() throws IOException, ConnectionCredentialsException {
    testGetPresignedUrlForFile((long)Math.pow(2, 30) * FILE_SIZE_TRESHOLD_IN_GB);
  }

  @Test
  public void testGetPresignedUrlForLargeFileCloudFront() throws IOException, ConnectionCredentialsException {
    testGetPresignedUrlForFileCloudFront((long)Math.pow(2, 30) * FILE_SIZE_TRESHOLD_IN_GB + 1);
  }

  @Test
  public void testGetPresignedUrlForNormalFileCloudFront() throws IOException, ConnectionCredentialsException {
    testGetPresignedUrlForFileCloudFront((long)Math.pow(2, 30) * FILE_SIZE_TRESHOLD_IN_GB);
  }

  private void testGetPresignedUrlForFile(long fileSize) throws IOException, ConnectionCredentialsException {
    HeadObjectResponse objectMetadata = HeadObjectResponse.builder().contentLength(fileSize).contentType(S3Util.DEFAULT_CONTENT_TYPE).build();
    int expectedTtlSeconds = ( fileSize > Math.pow(2, 30) * TeamCityProperties.getInteger(S3_DOWNLOAD_THRESHOLD_FOR_PRESIGN_URL_EXTENSION_IN_GB) ) ? myS3Settings.getUrlExtendedTtlSeconds() : myS3Settings.getUrlTtlSeconds();
    TimeService timeService = new MockTimeService();
    Map<String, String> settings = myS3Settings.toRawSettings();
    Mockito.when(myAmazonS3Provider.withS3ClientShuttingDownImmediately(Mockito.eq(settings), Mockito.eq(PROJECT_ID), Mockito.any(WithS3Client.class))).thenReturn(objectMetadata, objectMetadata, HeadBucketResponse.builder().build());
    Mockito.when(myAmazonS3Provider.withS3PresignerShuttingDownImmediately(Mockito.eq(BUCKET_NAME), Mockito.eq(settings), Mockito.eq(PROJECT_ID), Mockito.any(WithS3Presigner.class))).thenReturn(PRESIGNED_URL);
    Map<String, List<String >> headers = new HashMap<>();
    headers.put("host", Arrays.asList(DOMAIN));
    PresignedGetObjectRequest expectedPresignedRequest = PresignedGetObjectRequest.builder()
                                                                                  .httpRequest(SdkHttpRequest.builder()
                                                                                                             .method(SdkHttpMethod.GET)
                                                                                                             .uri(PRESIGNED_URL)
                                                                                                             .build()
                                                                                  )
                                                                                  .expiration(new Date(timeService.now() + expectedTtlSeconds * 1000L).toInstant())
                                                                                  .signedHeaders(headers)
                                                                                  .isBrowserExecutable(false)
                                                                                  .build();
    Mockito.when(myS3Presigner.presignGetObject(Mockito.any(GetObjectPresignRequest.class))).thenReturn(expectedPresignedRequest);

    S3PresignedUrlProvider s3PresignedUrlProvider = new S3PresignedUrlProviderImpl(myAmazonS3Provider);
    s3PresignedUrlProvider.generateDownloadUrl(SdkHttpMethod.GET, OBJECT_KEY, myS3Settings);

    ArgumentCaptor<WithS3Presigner<S3Presigner, ConnectionCredentialsException>> s3PresignerCaptor = ArgumentCaptor.forClass(WithS3Presigner.class);
    Mockito.verify(myAmazonS3Provider).withS3PresignerShuttingDownImmediately(Mockito.eq(BUCKET_NAME), Mockito.eq(settings), Mockito.eq(PROJECT_ID), s3PresignerCaptor.capture());
    s3PresignerCaptor.getValue().execute(myS3Presigner);
    ArgumentCaptor<GetObjectPresignRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
    Mockito.verify(myS3Presigner).presignGetObject(requestCaptor.capture());

    Duration presignedUrlTtl = requestCaptor.getValue().signatureDuration();
    Assert.assertEquals(presignedUrlTtl, Duration.ofSeconds(expectedTtlSeconds));
  }

  private void testGetPresignedUrlForFileCloudFront(long fileSize) throws IOException, ConnectionCredentialsException {
    HeadObjectResponse objectMetadata = HeadObjectResponse.builder().contentLength(fileSize).contentType(S3Util.DEFAULT_CONTENT_TYPE).build();
    Map<String, String> settings = myS3Settings.toRawSettings();

    Mockito.when(myAmazonS3Provider.withCloudFrontClient(Mockito.eq(settings), Mockito.eq(PROJECT_ID), Mockito.any(WithCloudFrontClient.class))).thenReturn(DOMAIN);
    Mockito.when(myAmazonS3Provider.withS3ClientShuttingDownImmediately(Mockito.eq(settings), Mockito.eq(PROJECT_ID), Mockito.any(WithS3Client.class))).thenReturn(objectMetadata);

    TimeService timeService = new MockTimeService();
    CloudFrontPresignedUrlProvider cloudFrontPresignedUrlProvider = new CloudFrontPresignedUrlProviderImpl(timeService, myAmazonS3Provider);
    cloudFrontPresignedUrlProvider.generateDownloadUrl(OBJECT_KEY, myS3Settings);

    // Could not mock and capture arguments of 3rd party final class CloudFrontUtilities without additional tools like PowerMock and extending PowerMockTestCase-like service classes.
    // Decided to verify the second to the last step: myS3Settings.getUrlTtlSeconds or myS3Settings.getUrlExtendedTtlSeconds
    if (fileSize > Math.pow(2, 30) * TeamCityProperties.getInteger(S3_DOWNLOAD_THRESHOLD_FOR_PRESIGN_URL_EXTENSION_IN_GB)) {
      Mockito.verify(myS3Settings).getUrlExtendedTtlSeconds();
      Mockito.verify(myS3Settings, Mockito.never()).getUrlTtlSeconds();
    } else {
      Mockito.verify(myS3Settings).getUrlTtlSeconds();
      Mockito.verify(myS3Settings, Mockito.never()).getUrlExtendedTtlSeconds();
    }
  }
}
