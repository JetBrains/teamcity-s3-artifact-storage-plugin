package jetbrains.buildServer.artifacts.s3;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import java.util.Collections;
import java.util.Map;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.artifacts.s3.CheckS3TransferAccelerationAvailability.S3AccelerationDto;
import jetbrains.buildServer.artifacts.s3.amazonClient.AmazonS3Provider;
import jetbrains.buildServer.artifacts.s3.amazonClient.WithS3Client;
import jetbrains.buildServer.serverSide.connections.credentials.ConnectionCredentialsException;
import org.mockito.Answers;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;


@Test
public class CheckS3TransferAccelerationAvailabilityTest extends BaseTestCase {

  private static final String PROJECT_ID = "projectId";
  private static final String BUCKET_NAME = "bucketName";
  private CheckS3TransferAccelerationAvailability myClient;
  private AmazonS3Provider myS3Provider;
  private AmazonS3Client myS3Client;

  @BeforeMethod(alwaysRun = true)
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myS3Provider = Mockito.mock(AmazonS3Provider.class);
    myS3Client = Mockito.mock(AmazonS3Client.class, Answers.RETURNS_DEEP_STUBS);
    myClient = new CheckS3TransferAccelerationAvailability(myS3Provider);
  }

  private Map<String, String> getMockParameters() {
    return Collections.singletonMap(S3Constants.S3_BUCKET_NAME, BUCKET_NAME);
  }

  private void callS3() throws ConnectionCredentialsException {
    Mockito.when(myS3Provider.withS3Client(Mockito.eq(getMockParameters()), Mockito.eq(PROJECT_ID), Mockito.any())).thenAnswer(invocation -> {
      return ((WithS3Client<?, ?>) invocation.getArgument(2)).execute(myS3Client);
    });
  }

  public void test() throws Exception {
    Mockito.when(myS3Client.getBucketAccelerateConfiguration(BUCKET_NAME).getStatus()).thenReturn("Enabled");
    callS3();

    S3AccelerationDto accelerationDto = myClient.fetchDto(getMockParameters(), PROJECT_ID);

    Assert.assertEquals(accelerationDto.getAccelerationStatus(), "Enabled");
    Assert.assertEquals(accelerationDto.getBucketName(), BUCKET_NAME);
  }

  public void testError501IsInterpretedAsDisaled() throws Exception {
    AmazonS3Exception error = new AmazonS3Exception("Error");
    error.setStatusCode(501);
    Mockito.when(myS3Client.getBucketAccelerateConfiguration(BUCKET_NAME).getStatus()).thenThrow(error);
    callS3();

    S3AccelerationDto accelerationDto = myClient.fetchDto(getMockParameters(), PROJECT_ID);

    Assert.assertEquals(accelerationDto.getAccelerationStatus(), "Disabled");
    Assert.assertEquals(accelerationDto.getBucketName(), BUCKET_NAME);
  }

  @Test(expectedExceptions = AmazonS3Exception.class)
  public void testOtherErrorsAreReportedBack() throws Exception {
    AmazonS3Exception error = new AmazonS3Exception("Error");
    Mockito.when(myS3Client.getBucketAccelerateConfiguration(BUCKET_NAME).getStatus()).thenThrow(error);
    callS3();

    S3AccelerationDto accelerationDto = myClient.fetchDto(getMockParameters(), PROJECT_ID);

    Assert.assertEquals(accelerationDto.getAccelerationStatus(), "Disabled");
    Assert.assertEquals(accelerationDto.getBucketName(), BUCKET_NAME);
  }
}
