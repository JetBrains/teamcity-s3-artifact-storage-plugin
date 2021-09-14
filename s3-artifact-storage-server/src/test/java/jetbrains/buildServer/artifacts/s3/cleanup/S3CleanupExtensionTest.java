package jetbrains.buildServer.artifacts.s3.cleanup;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import java.util.concurrent.atomic.AtomicInteger;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.artifacts.ArtifactData;
import jetbrains.buildServer.artifacts.ArtifactDataInstance;
import jetbrains.buildServer.artifacts.ArtifactListData;
import jetbrains.buildServer.artifacts.ServerArtifactStorageSettingsProvider;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.serverSide.CleanupLevel;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.artifacts.ServerArtifactHelper;
import jetbrains.buildServer.serverSide.cleanup.BuildCleanupContext;
import jetbrains.buildServer.serverSide.cleanup.BuildCleanupContextEx;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.serverSide.impl.FinishedBuildEx;
import jetbrains.buildServer.serverSide.impl.cleanup.CleanupProcessStateEx;
import org.jetbrains.annotations.NotNull;
import org.jmock.Mock;
import org.jmock.core.stub.DefaultResultStub;
import org.junit.AfterClass;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.*;
import java.util.concurrent.*;

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;


public class S3CleanupExtensionTest extends BaseTestCase {
  public static final String BUCKET_NAME = "foo";
  public static final DockerImageName LOCALSTACK_IMAGE = DockerImageName.parse("localstack/localstack:0.11.3");

  public static final ScheduledThreadPoolExecutor SCHEDULED_EXECUTOR = new ScheduledThreadPoolExecutor(4);
  public static final ExecutorService SINGLE_THREAD_EXECUTOR = Executors.newSingleThreadExecutor();

  public static final ExecutorServices EXECUTOR_SERVICES = new ExecutorServices() {
    @NotNull
    @Override
    public ScheduledExecutorService getNormalExecutorService() {
      return SCHEDULED_EXECUTOR;
    }

    @NotNull
    @Override
    public ExecutorService getLowPriorityExecutorService() {
      return SINGLE_THREAD_EXECUTOR;
    }
  };

  public static final LocalStackContainer LOCALSTACK = new LocalStackContainer(LOCALSTACK_IMAGE).withServices(S3);

  @BeforeClass
  @Override
  protected void setUpClass() throws Exception {
    super.setUpClass();

    LOCALSTACK.start();

    int maxWaitTime = 1000;
    int waitTime = 0;
    while (!LOCALSTACK.isRunning() && waitTime < maxWaitTime) {
      Thread.sleep(200);
      waitTime += 200;
    }
  }

  @AfterClass
  public static void cleanup() {
    LOCALSTACK.close();
    SCHEDULED_EXECUTOR.shutdown();
    SINGLE_THREAD_EXECUTOR.shutdown();
  }

  @Test
  public void deletesArtifactsFromS3WithRetry() {
    AWSCredentialsProvider credentialsProvider = LOCALSTACK.getDefaultCredentialsProvider();
    AwsClientBuilder.EndpointConfiguration endpointConfiguration = LOCALSTACK.getEndpointConfiguration(S3);

    Map<String, String> storageSettings = getStorageSettings(credentialsProvider, endpointConfiguration);
    storageSettings.put("teamcity.internal.storage.s3.upload.retryDelayMs", "500");
    storageSettings.put("teamcity.internal.storage.s3.upload.numberOfRetries", "50");

    String artifactPath = "bar";
    String expectedContents = "baz";

    ArtifactDataInstance artifact = ArtifactDataInstance.create(artifactPath, expectedContents.length());
    S3CleanupExtension cleanupExtension = getCleanupExtension(storageSettings, artifact);

    AmazonS3 s3 = getS3Client(credentialsProvider, endpointConfiguration);

    cleanupExtension.registerListener(new AbstractCleanupListener() {
      @Override
      public void onError(Exception exception, boolean isRecoverable) {
        if (exception instanceof AmazonS3Exception && isRecoverable) {
          s3.createBucket(BUCKET_NAME);
          s3.putObject(BUCKET_NAME, artifactPath, expectedContents);
        }
      }
    });

    Mock contextMock = getContextMock();
    contextMock.stubs().method("onBuildCleanupError").will(throwException(new RuntimeException("Build cleanup error")));
    BuildCleanupContext context = (BuildCleanupContext)contextMock.proxy();

    cleanupExtension.cleanupBuildsData(context);

    assertFalse(s3.doesObjectExist(BUCKET_NAME, artifactPath));
  }


  @Test
  public void failsBecauseRetryDoesntHaveEnoughTime() {
    if (myTestLogger != null) {
      myTestLogger.doNotFailOnErrorMessages();
    }
    AWSCredentialsProvider credentialsProvider = LOCALSTACK.getDefaultCredentialsProvider();
    AwsClientBuilder.EndpointConfiguration endpointConfiguration = LOCALSTACK.getEndpointConfiguration(S3);

    Map<String, String> storageSettings = getStorageSettings(credentialsProvider, endpointConfiguration);
    storageSettings.put("teamcity.internal.storage.s3.upload.retryDelayMs", "200");
    storageSettings.put("teamcity.internal.storage.s3.upload.numberOfRetries", "5");

    String artifactPath = "bar";
    String expectedContents = "baz";

    ArtifactDataInstance artifact = ArtifactDataInstance.create(artifactPath, expectedContents.length());
    S3CleanupExtension cleanupExtension = getCleanupExtension(storageSettings, artifact);

    AtomicInteger tryCount = new AtomicInteger(0);

    cleanupExtension.registerListener(new AbstractCleanupListener() {
      @Override
      public void onError(Exception exception, boolean isRecoverable) {
        if (exception instanceof AmazonS3Exception && isRecoverable) {
          tryCount.incrementAndGet();
        }
      }
    });

    Mock contextMock = getContextMock();
    contextMock.stubs().method("onBuildCleanupError");

    BuildCleanupContext context = (BuildCleanupContext)contextMock.proxy();

    AmazonS3 s3 = getS3Client(credentialsProvider, endpointConfiguration);

    cleanupExtension.cleanupBuildsData(context);
    assertEquals("Should try deleting object for 6 times", 6, tryCount.get());
  }

  @NotNull
  private HashMap<String, String> getStorageSettings(AWSCredentialsProvider credentialsProvider, AwsClientBuilder.EndpointConfiguration endpointConfiguration) {
    HashMap<String, String> storageSettings = new HashMap<>();
    storageSettings.put("aws.region.name", endpointConfiguration.getSigningRegion());
    storageSettings.put("secure:aws.secret.access.key", credentialsProvider.getCredentials().getAWSSecretKey());
    storageSettings.put("aws.access.key.id", credentialsProvider.getCredentials().getAWSAccessKeyId());
    storageSettings.put("aws.credentials.type", "aws.access.keys");
    storageSettings.put("storage.s3.bucket.name", BUCKET_NAME);
    storageSettings.put("aws.environment", "custom");
    storageSettings.put("aws.service.endpoint", endpointConfiguration.getServiceEndpoint());
    return storageSettings;
  }

  private AmazonS3 getS3Client(AWSCredentialsProvider credentialsProvider, AwsClientBuilder.EndpointConfiguration endpointConfiguration) {
    return AmazonS3ClientBuilder
      .standard()
      .withEndpointConfiguration(endpointConfiguration)
      .withCredentials(credentialsProvider)
      .build();
  }

  @NotNull
  private Mock getContextMock() {
    Mock cleanupState = mock(CleanupProcessStateEx.class);

    Mock build = mock(FinishedBuildEx.class);
    build.stubs().method("getBuildNumber").will(returnValue("1"));
    build.stubs().method("getBuildId").will(returnValue(1L));
    build.stubs().method("getBuildTypeExternalId").will(returnValue("id"));

    Mock context = mock(BuildCleanupContextEx.class);

    context.stubs().method("getBuilds").will(returnValue(Collections.singletonList((FinishedBuildEx)build.proxy())));
    context.stubs().method("getCleanupLevel").will(returnValue(CleanupLevel.EVERYTHING));

    cleanupState.setDefaultStub(new DefaultResultStub());
    context.stubs().method("getCleanupState").will(returnValue((CleanupProcessStateEx)cleanupState.proxy()));
    return context;
  }

  @NotNull
  private S3CleanupExtension getCleanupExtension(Map<String, String> storageSettings, ArtifactData... artifacts) {
    Mock artifactHelper = mock(ServerArtifactHelper.class);

    artifactHelper.stubs().method("getArtifactList").will(returnValue(getArtifactListData(artifacts)));
    artifactHelper.stubs().method("removeFromArtifactList");

    Mock settingsProvider = mock(ServerArtifactStorageSettingsProvider.class);

    settingsProvider.stubs().method("getStorageSettings").will(returnValue(storageSettings));

    ServerPaths serverPaths = new ServerPaths("./target");

    return new S3CleanupExtension((ServerArtifactHelper)artifactHelper.proxy(), (ServerArtifactStorageSettingsProvider)settingsProvider.proxy(), serverPaths, EXECUTOR_SERVICES);
  }

  @NotNull
  private ArtifactListData getArtifactListData(ArtifactData... artifacts) {
    HashMap<String, String> props = new HashMap<>();
    props.put(S3Constants.S3_PATH_PREFIX_ATTR, "");

    return new ArtifactListData() {
      @NotNull
      @Override
      public List<ArtifactData> getArtifactList() {
        return Arrays.asList(artifacts);
      }

      @NotNull
      @Override
      public Map<String, String> getCommonProperties() {
        return props;
      }

      @NotNull
      @Override
      public String getStorageSettingsId() {
        return "";
      }
    };
  }

}
