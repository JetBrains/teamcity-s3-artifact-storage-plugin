package jetbrains.buildServer.artifacts.s3.cloudfront;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.MockTimeService;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.S3PresignedUrlProvider;
import jetbrains.buildServer.artifacts.s3.S3PresignedUrlProviderImpl;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.artifacts.s3.amazonClient.AmazonS3Provider;
import jetbrains.buildServer.artifacts.s3.amazonClient.WithS3Client;
import jetbrains.buildServer.serverSide.connections.credentials.ConnectionCredentialsException;
import jetbrains.buildServer.util.TimeService;
import org.junit.Assert;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.artifacts.s3.S3Constants.*;
import static jetbrains.buildServer.artifacts.s3.cloudfront.CloudFrontConstants.*;

public class PresignedUrlsProviderDownloadTest extends BaseTestCase {

  private static final String PROJECT_ID = "projectId";
  private static final String OBJECT_KEY = "objectKey";
  private static final String BUCKET_NAME = "bucketName";
  private static final String REGION = "test-region-1";
  private static final String USER_AGENT = "Chrome";
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

  private AmazonS3Provider myAmazonS3Provider;
  private AmazonS3 myS3Client;
  private CloudFrontSettings myS3Settings;

  @BeforeMethod(alwaysRun = true)
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setInternalProperty(S3_URL_LIFETIME_SEC, "600");
    setInternalProperty(S3_URL_LIFETIME_EXTENDED_SEC, "10800");
    myAmazonS3Provider = Mockito.mock(AmazonS3Provider.class);
    myS3Client = Mockito.mock(AmazonS3.class);
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
    testGetPresignedUrlForFile((long)Math.pow(2, 30) * S3Constants.S3_DOWNLOAD_THRESHOLD_FOR_PRESIGN_URL_EXTENSION_IN_GB + 1);
  }

  @Test
  public void testGetPresignedUrlForNormalFile() throws IOException, ConnectionCredentialsException {
    testGetPresignedUrlForFile((long)Math.pow(2, 30) * S3Constants.S3_DOWNLOAD_THRESHOLD_FOR_PRESIGN_URL_EXTENSION_IN_GB);
  }

  @Test
  public void testGetPresignedUrlForLargeFileCloudFront() throws IOException, ConnectionCredentialsException {
    testGetPresignedUrlForFileCloudFront((long)Math.pow(2, 30) * S3Constants.S3_DOWNLOAD_THRESHOLD_FOR_PRESIGN_URL_EXTENSION_IN_GB + 1);
  }

  @Test
  public void testGetPresignedUrlForNormalFileCloudFront() throws IOException, ConnectionCredentialsException {
    testGetPresignedUrlForFileCloudFront((long)Math.pow(2, 30) * S3Constants.S3_DOWNLOAD_THRESHOLD_FOR_PRESIGN_URL_EXTENSION_IN_GB);
  }

  private void testGetPresignedUrlForFile(long fileSize) throws IOException, ConnectionCredentialsException {
    ObjectMetadata objectMetadata = new ObjectMetadata();
    objectMetadata.setContentLength(fileSize);
    objectMetadata.setContentType(S3Util.DEFAULT_CONTENT_TYPE);

    Mockito.when(myS3Client.generatePresignedUrl(Mockito.any(GeneratePresignedUrlRequest.class))).thenReturn(new URL(PRESIGNED_URL));
    Mockito.when(myAmazonS3Provider.withS3ClientShuttingDownImmediately(Mockito.eq(myS3Settings.toRawSettings()), Mockito.eq(PROJECT_ID), Mockito.any())).thenReturn(objectMetadata, objectMetadata, PRESIGNED_URL);

    TimeService timeService = new MockTimeService();
    S3PresignedUrlProvider s3PresignedUrlProvider = new S3PresignedUrlProviderImpl(timeService, myAmazonS3Provider);
    s3PresignedUrlProvider.generateDownloadUrl(HttpMethod.GET, OBJECT_KEY, myS3Settings);

    ArgumentCaptor<WithS3Client<AmazonS3, ConnectionCredentialsException>> s3ClientCaptor = ArgumentCaptor.forClass(WithS3Client.class);
    Mockito.verify(myAmazonS3Provider, Mockito.times(3)).withS3ClientShuttingDownImmediately(Mockito.eq(myS3Settings.toRawSettings()), Mockito.eq(PROJECT_ID), s3ClientCaptor.capture());
    s3ClientCaptor.getValue().execute(myS3Client);
    ArgumentCaptor<GeneratePresignedUrlRequest> requestCaptor = ArgumentCaptor.forClass(GeneratePresignedUrlRequest.class);
    Mockito.verify(myS3Client).generatePresignedUrl(requestCaptor.capture());

    Date presignedUrlTtl = requestCaptor.getValue().getExpiration();
    int expectedTtlSeconds = ( fileSize > Math.pow(2, 30) * S3Constants.S3_DOWNLOAD_THRESHOLD_FOR_PRESIGN_URL_EXTENSION_IN_GB ) ? myS3Settings.getUrlExtendedTtlSeconds() : myS3Settings.getUrlTtlSeconds();
    Assert.assertEquals(presignedUrlTtl, new Date(timeService.now() + expectedTtlSeconds * 1000L));
  }

  private void testGetPresignedUrlForFileCloudFront(long fileSize) throws IOException, ConnectionCredentialsException {
    ObjectMetadata objectMetadata = new ObjectMetadata();
    objectMetadata.setContentLength(fileSize);

    Mockito.when(myAmazonS3Provider.withCloudFrontClient(Mockito.eq(myS3Settings.toRawSettings()), Mockito.eq(PROJECT_ID), Mockito.any())).thenReturn("RandomDomain");
    Mockito.when(myAmazonS3Provider.withS3ClientShuttingDownImmediately(Mockito.eq(myS3Settings.toRawSettings()), Mockito.eq(PROJECT_ID), Mockito.any())).thenReturn(objectMetadata);

    TimeService timeService = new MockTimeService();
    CloudFrontPresignedUrlProvider cloudFrontPresignedUrlProvider = new CloudFrontPresignedUrlProviderImpl(timeService, myAmazonS3Provider);
    cloudFrontPresignedUrlProvider.generateDownloadUrl(OBJECT_KEY, myS3Settings);

    // Could not mock and capture arguments of 3rd party static method CloudFrontUrlSigner.getSignedURLWithCannedPolicy without additional tools like PowerMock and
    // extending PowerMockTestCase-like service classes. Decided to verify the second to the last step before request to S3: myS3Settings.getUrlTtlSeconds or myS3Settings.getUrlExtendedTtlSeconds
    if (fileSize > Math.pow(2, 30) * S3Constants.S3_DOWNLOAD_THRESHOLD_FOR_PRESIGN_URL_EXTENSION_IN_GB) {
      Mockito.verify(myS3Settings).getUrlExtendedTtlSeconds();
      Mockito.verify(myS3Settings, Mockito.never()).getUrlTtlSeconds();
    } else {
      Mockito.verify(myS3Settings).getUrlTtlSeconds();
      Mockito.verify(myS3Settings, Mockito.never()).getUrlExtendedTtlSeconds();
    }
  }
}
