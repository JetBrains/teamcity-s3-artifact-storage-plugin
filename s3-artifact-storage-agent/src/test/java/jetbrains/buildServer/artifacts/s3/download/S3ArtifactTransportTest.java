package jetbrains.buildServer.artifacts.s3.download;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.artifacts.FileProgress;
import jetbrains.buildServer.artifacts.ProgressTrackingURLContentRetriever;
import jetbrains.buildServer.artifacts.URLContentRetriever;
import jetbrains.buildServer.artifacts.impl.DependencyHttpHelper;
import jetbrains.buildServer.artifacts.s3.download.parallel.strategy.ParallelDownloadStrategy;
import jetbrains.buildServer.artifacts.s3.download.parallel.strategy.impl.InplaceParallelDownloadStrategy;
import jetbrains.buildServer.artifacts.s3.download.parallel.strategy.impl.SeparatePartFilesParallelDownloadStrategy;
import jetbrains.buildServer.http.HttpUtil;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.*;

import static jetbrains.buildServer.artifacts.s3.S3Constants.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

@Test
public class S3ArtifactTransportTest {
  private static final String BUCKET_NAME = "test-bucket";
  private static final int ONE_MEGABYTE = 1024 * 1024;

  private final TempFiles perClassTempFiles = new TempFiles();
  private final TempFiles perMethodTempFiles = new TempFiles();

  private S3MockContainer s3Container;
  private Path sourceFilesTempDir;
  private Path targetFilesDir;
  private AmazonS3 s3Client;
  private ExecutorService executorService;
  private MultiThreadedHttpConnectionManager httpConnectionManager;
  private HttpClient httpClient;
  private AgentRunningBuild runningBuildMock;
  private ParallelDownloadStrategy inplaceStrategySpy;
  private ParallelDownloadStrategy separatePartFIlesStrategySpy;
  private Map<String, ParallelDownloadStrategy> parallelDownloadStrategySpies;
  private DependencyHttpHelper dependencyHttpHelperMock;

  @BeforeClass
  public void setUpClass() throws IOException {
    sourceFilesTempDir = perClassTempFiles.createTempDir().toPath();

    s3Container = new S3MockContainer("3.12.0")
      .withInitialBuckets(BUCKET_NAME)
      .withRetainFilesOnExit(false);
    s3Container.start();

    s3Client = AmazonS3ClientBuilder.standard()
      .withEndpointConfiguration(new AmazonS3ClientBuilder.EndpointConfiguration(s3Container.getHttpEndpoint(), "us-east-1"))
      .enablePathStyleAccess()
      .build();

    executorService = Executors.newFixedThreadPool(10);

    httpConnectionManager = (MultiThreadedHttpConnectionManager)HttpUtil.createMultiThreadedHttpConnectionManager(60);
    httpConnectionManager.getParams().setDefaultMaxConnectionsPerHost(100);
    httpConnectionManager.getParams().setMaxTotalConnections(100);

    httpClient = new HttpClient();
    httpClient.setHttpConnectionManager(httpConnectionManager);

    generateTestFiles();
    uploadTestFilesToS3();
  }

  private void generateTestFiles() throws IOException {
    byte[] oneMegabyteSyntheticData = new byte[ONE_MEGABYTE];
    for (int i = 0; i < ONE_MEGABYTE; i++) {
      oneMegabyteSyntheticData[i] = (byte)i;
    }

    for (TestFile file : TestFile.values()) {
      String fileName = file.getName();
      Path sourceFile = sourceFilesTempDir.resolve(fileName);
      writeSyntheticData(sourceFile, file.getSize(), oneMegabyteSyntheticData);
    }
  }

  private void uploadTestFilesToS3() {
    for (TestFile file : TestFile.values()) {
      String fileName = file.getName();
      Path sourceFile = sourceFilesTempDir.resolve(fileName);
      s3Client.putObject(BUCKET_NAME, fileName, sourceFile.toFile());
    }
  }

  @AfterClass
  public void tearDownTests() {
    s3Container.stop();
    s3Client.shutdown();
    perClassTempFiles.cleanup();
    executorService.shutdownNow();
    httpConnectionManager.shutdown();
  }

  @BeforeMethod
  public void setUp() throws IOException {
    targetFilesDir = perMethodTempFiles.createTempDir().toPath();

    runningBuildMock = mock(AgentRunningBuild.class);
    when(runningBuildMock.getSharedConfigParameters()).thenReturn(Collections.emptyMap());
    when(runningBuildMock.getBuildTempDirectory()).thenReturn(perMethodTempFiles.createTempDir().toPath().toFile());

    InplaceParallelDownloadStrategy inplaceStrategy = new InplaceParallelDownloadStrategy();
    SeparatePartFilesParallelDownloadStrategy separatePartFilesStrategy = new SeparatePartFilesParallelDownloadStrategy();
    inplaceStrategySpy = spy(inplaceStrategy);
    separatePartFIlesStrategySpy = spy(separatePartFilesStrategy);
    parallelDownloadStrategySpies = new HashMap<String, ParallelDownloadStrategy>() {{
      put(inplaceStrategy.getName(), inplaceStrategySpy);
      put(separatePartFilesStrategy.getName(), separatePartFIlesStrategySpy);
    }};

    dependencyHttpHelperMock = mock(DependencyHttpHelper.class);
  }

  @AfterMethod
  public void tearDown() {
    perMethodTempFiles.cleanup();
  }

  @DataProvider
  public Object[][] sequentialDownloadTestData() {
    return new Object[][]{
      {TestFile.FILE_0_B, 1, 5, 10},
      {TestFile.FILE_10_123_B, 1, 5, 100},
      {TestFile.FILE_2_234_456_B, 2, 5, 10},
      {TestFile.FILE_2_234_456_B, 1, 1, 10},
      {TestFile.FILE_23_001_012_B, 25, 5, 1_000},
      {TestFile.FILE_23_001_012_B, 100, 5, 100},
      {TestFile.FILE_23_001_012_B, 12, 1, 10},
      {TestFile.FILE_23_001_012_B, 1, 1, 10},
      {TestFile.FILE_107_231_345_B, 150, 5, 10_000},
      {TestFile.FILE_107_231_345_B, 20, 1, 10},
    };
  }

  @Test(dataProvider = "sequentialDownloadTestData")
  public void shouldDownloadFileSequentially(TestFile testFile, int minPartSizeMB, int maxThreads, int bufferSizeKB) throws IOException {
    // arrange
    String fileName = testFile.getName();
    Path sourceFile = sourceFilesTempDir.resolve(fileName);
    Path targetFile = targetFilesDir.resolve(fileName);
    URL presignedUrl = createPresignedUrl(fileName);
    SimpleFileProgress progress = new SimpleFileProgress();

    when(runningBuildMock.getSharedConfigParameters()).thenReturn(new HashMap<String, String>() {{
      put(S3_PARALLEL_DOWNLOAD_MIN_PART_SIZE_MB, Integer.toString(minPartSizeMB));
      put(S3_PARALLEL_DOWNLOAD_MAX_THREADS, Integer.toString(maxThreads));
      put(S3_PARALLEL_DOWNLOAD_BUFFER_SIZE_KB, Integer.toString(bufferSizeKB));
    }});

    ProgressTrackingURLContentRetriever instance = createInstance(runningBuildMock, parallelDownloadStrategySpies);

    // act
    instance.downloadUrlTo(presignedUrl.toString(), targetFile.toFile(), progress);

    // assert
    assertFileDownloadedCorrectly(targetFile, sourceFile);
    assertEquals(progress.getTransferred(), Files.size(sourceFile));
    verifyNoInteractions(inplaceStrategySpy);
    verifyNoInteractions(separatePartFIlesStrategySpy);
  }

  @DataProvider
  public Object[][] parallelDownloadTestData() {
    return new Object[][]{
      {TestFile.FILE_2_234_456_B, 1, 5, 1, InplaceParallelDownloadStrategy.NAME},
      {TestFile.FILE_2_234_456_B, 1, 50, 10, InplaceParallelDownloadStrategy.NAME},
      {TestFile.FILE_2_234_456_B, 1, 500, 10, InplaceParallelDownloadStrategy.NAME},
      {TestFile.FILE_23_001_012_B, 12, 5, 10, InplaceParallelDownloadStrategy.NAME},
      {TestFile.FILE_23_001_012_B, 12, 50, 100, InplaceParallelDownloadStrategy.NAME},
      {TestFile.FILE_23_001_012_B, 12, 500, 10_000, InplaceParallelDownloadStrategy.NAME},
      {TestFile.FILE_23_001_012_B, 1, 5, 10_000, InplaceParallelDownloadStrategy.NAME},
      {TestFile.FILE_107_231_345_B, 100, 5, 10, InplaceParallelDownloadStrategy.NAME},
      {TestFile.FILE_107_231_345_B, 10, 5, 10_000, InplaceParallelDownloadStrategy.NAME},
      {TestFile.FILE_107_231_345_B, 10, 500, 10, InplaceParallelDownloadStrategy.NAME},

      {TestFile.FILE_2_234_456_B, 1, 5, 1, SeparatePartFilesParallelDownloadStrategy.NAME},
      {TestFile.FILE_2_234_456_B, 1, 50, 10, SeparatePartFilesParallelDownloadStrategy.NAME},
      {TestFile.FILE_2_234_456_B, 1, 500, 10, SeparatePartFilesParallelDownloadStrategy.NAME},
      {TestFile.FILE_23_001_012_B, 12, 5, 10, SeparatePartFilesParallelDownloadStrategy.NAME},
      {TestFile.FILE_23_001_012_B, 12, 50, 100, SeparatePartFilesParallelDownloadStrategy.NAME},
      {TestFile.FILE_23_001_012_B, 12, 500, 10_000, SeparatePartFilesParallelDownloadStrategy.NAME},
      {TestFile.FILE_23_001_012_B, 1, 5, 10_000, SeparatePartFilesParallelDownloadStrategy.NAME},
      {TestFile.FILE_107_231_345_B, 100, 5, 10, SeparatePartFilesParallelDownloadStrategy.NAME},
      {TestFile.FILE_107_231_345_B, 10, 5, 10_000, SeparatePartFilesParallelDownloadStrategy.NAME},
      {TestFile.FILE_107_231_345_B, 10, 500, 10, SeparatePartFilesParallelDownloadStrategy.NAME},
    };
  }

  @Test(dataProvider = "parallelDownloadTestData")
  public void shouldDownloadFileInParallel(TestFile testFile, int minPartSizeMB, int maxThreads, int bufferSizeKB, String parallelStrategy) throws IOException {
    // arrange
    String fileName = testFile.getName();
    Path sourceFile = sourceFilesTempDir.resolve(fileName);
    Path targetFile = targetFilesDir.resolve(fileName);
    URL presignedUrl = createPresignedUrl(fileName);
    SimpleFileProgress progress = new SimpleFileProgress();

    when(runningBuildMock.getSharedConfigParameters()).thenReturn(new HashMap<String, String>() {{
      put(S3_PARALLEL_DOWNLOAD_MIN_PART_SIZE_MB, Integer.toString(minPartSizeMB));
      put(S3_PARALLEL_DOWNLOAD_MAX_THREADS, Integer.toString(maxThreads));
      put(S3_PARALLEL_DOWNLOAD_BUFFER_SIZE_KB, Integer.toString(bufferSizeKB));
      put(S3_PARALLEL_DOWNLOAD_STRATEGY, parallelStrategy);
    }});

    ProgressTrackingURLContentRetriever instance = createInstance(runningBuildMock, parallelDownloadStrategySpies);

    // act
    instance.downloadUrlTo(presignedUrl.toString(), targetFile.toFile(), progress);

    // assert
    assertFileDownloadedCorrectly(targetFile, sourceFile);
    assertEquals(progress.getTransferred(), Files.size(sourceFile));
    if (parallelStrategy.equals(InplaceParallelDownloadStrategy.NAME)) {
      verify(inplaceStrategySpy).download(any(), any(), anyLong(), any(), any());
    }
    if (parallelStrategy.equals(SeparatePartFilesParallelDownloadStrategy.NAME)) {
      verify(separatePartFIlesStrategySpy).download(any(), any(), anyLong(), any(), any());
    }
  }

  @DataProvider
  public Object[][] overwriteFileTestData() {
    return new Object[][]{
      {TestFile.FILE_0_B, 10, null}, // sequential, empty file
      {TestFile.FILE_10_123_B, 10_000, null}, // sequential, normal file, fewer bytes
      {TestFile.FILE_10_123_B, 10_200, null}, // sequential, normal file, more bytes
      {TestFile.FILE_2_234_456_B, 2_234_300, InplaceParallelDownloadStrategy.NAME}, // parallel inplace, fewer bytes
      {TestFile.FILE_2_234_456_B, 2_234_500, InplaceParallelDownloadStrategy.NAME}, // parallel inplace, more bytes
      {TestFile.FILE_2_234_456_B, 2_234_300, SeparatePartFilesParallelDownloadStrategy.NAME}, // paralle separate part files, fewer bytes
      {TestFile.FILE_2_234_456_B, 2_234_500, SeparatePartFilesParallelDownloadStrategy.NAME}, // paralle separate part files, more bytes
    };
  }

  @Test(dataProvider = "overwriteFileTestData")
  public void shouldDownloadAndOverwriteFileWhenExists(TestFile testFile, int existingFileSize, String parallelStrategy) throws IOException {
    // arrange
    String fileName = testFile.getName();
    Path sourceFile = sourceFilesTempDir.resolve(fileName);
    Path targetFile = targetFilesDir.resolve(fileName);
    URL presignedUrl = createPresignedUrl(fileName);
    SimpleFileProgress progress = new SimpleFileProgress();

    when(runningBuildMock.getSharedConfigParameters()).thenReturn(new HashMap<String, String>() {{
      put(S3_PARALLEL_DOWNLOAD_MIN_PART_SIZE_MB, Integer.toString(1));
      put(S3_PARALLEL_DOWNLOAD_MAX_THREADS, Integer.toString(5));
      put(S3_PARALLEL_DOWNLOAD_STRATEGY, parallelStrategy);
    }});

    Files.write(targetFile, new byte[existingFileSize]); // create existing file

    ProgressTrackingURLContentRetriever instance = createInstance(runningBuildMock, parallelDownloadStrategySpies);

    // act
    instance.downloadUrlTo(presignedUrl.toString(), targetFile.toFile(), progress);

    // assert
    assertFileDownloadedCorrectly(targetFile, sourceFile);
    assertEquals(progress.getTransferred(), Files.size(sourceFile));
    if (parallelStrategy != null) {
      if (parallelStrategy.equals(InplaceParallelDownloadStrategy.NAME)) {
        verify(inplaceStrategySpy).download(any(), any(), anyLong(), any(), any());
      }
      if (parallelStrategy.equals(SeparatePartFilesParallelDownloadStrategy.NAME)) {
        verify(separatePartFIlesStrategySpy).download(any(), any(), anyLong(), any(), any());
      }
    } else {
      verifyNoInteractions(inplaceStrategySpy);
      verifyNoInteractions(separatePartFIlesStrategySpy);
    }
  }

  @Test(expectedExceptions = IOException.class)
  public void shouldThrowExceptionWhenFileNotExistsAtSrcUrl() throws IOException {
    // arrange
    String fileName = "non-existing-file.sh";
    Path targetFile = targetFilesDir.resolve(fileName);
    URL presignedUrl = createPresignedUrl(fileName);
    SimpleFileProgress progress = new SimpleFileProgress();

    ProgressTrackingURLContentRetriever instance = createInstance(runningBuildMock, parallelDownloadStrategySpies);

    // act
    instance.downloadUrlTo(presignedUrl.toString(), targetFile.toFile(), progress);
  }

  @Test(expectedExceptions = IOException.class)
  public void shouldThrowExceptionWhenInterrupted() throws IOException {
    // arrange
    String fileName = TestFile.FILE_2_234_456_B.getName();
    Path targetFile = targetFilesDir.resolve(fileName);
    URL presignedUrl = createPresignedUrl(fileName);

    when(runningBuildMock.getSharedConfigParameters()).thenReturn(new HashMap<String, String>() {{
      put(S3_PARALLEL_DOWNLOAD_MIN_PART_SIZE_MB, Integer.toString(1));
      put(S3_PARALLEL_DOWNLOAD_MAX_THREADS, Integer.toString(5));
    }});

    URLContentRetriever instance = createInstance(runningBuildMock, parallelDownloadStrategySpies);
    instance.interrupt();

    // act
    instance.downloadUrlTo(presignedUrl.toString(), targetFile.toFile());
  }

  private S3ArtifactTransport createInstance(AgentRunningBuild runningBuild, Map<String, ParallelDownloadStrategy> parallelDownloadStrategiesByName) {
    S3DownloadConfiguration configuration = new S3DownloadConfiguration(runningBuild);
    return new S3ArtifactTransport(
      "https://i-am-teamcity-server",
      httpClient,
      executorService,
      dependencyHttpHelperMock,
      configuration,
      runningBuild,
      parallelDownloadStrategiesByName
    );
  }

  private void assertFileDownloadedCorrectly(Path targetFile, Path sourceFile) throws IOException {
    assertTrue(Files.exists(targetFile));
    assertEquals(Files.size(targetFile), Files.size(sourceFile));
    assertTrue(isSameContent(targetFile, sourceFile));
  }

  private URL createPresignedUrl(@NotNull String key) {
    GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(BUCKET_NAME, key)
      .withMethod(HttpMethod.GET)
      .withExpiration(Date.from(Instant.now().plusSeconds(300)));

    return s3Client.generatePresignedUrl(request);
  }

  private void writeSyntheticData(@NotNull Path file, int dataSize, byte[] syntheticDataBasis) throws IOException {
    try (OutputStream outputStream = Files.newOutputStream(file)) {
      int bytesWritten = 0;
      while (bytesWritten < dataSize) {
        int bytesRemaining = dataSize - bytesWritten;
        int bytesToWrite = Math.min(syntheticDataBasis.length, bytesRemaining);
        outputStream.write(syntheticDataBasis, 0, bytesToWrite);
        bytesWritten += bytesToWrite;
      }
    }
  }

  private boolean isSameContent(@NotNull Path firstFile, @NotNull Path secondFile) throws IOException {
    try (InputStream firstFileStream = new BufferedInputStream(Files.newInputStream(firstFile));
         InputStream secondFileStream = new BufferedInputStream(Files.newInputStream(secondFile))) {
      byte[] firstBuffer = new byte[ONE_MEGABYTE];
      byte[] secondBuffer = new byte[ONE_MEGABYTE];
      while (true) {
        int firstReadBytes = firstFileStream.read(firstBuffer);
        int secondReadBytes = secondFileStream.read(secondBuffer);
        if (firstReadBytes != secondReadBytes) {
          return false;
        } else if (firstReadBytes == -1) {
          return true;
        } else {
          if (!Arrays.equals(firstBuffer, secondBuffer)) return false;
        }
      }
    }
  }

  private static class SimpleFileProgress extends FileProgress.Adapter {
    private final AtomicLong transferredBytes = new AtomicLong();

    @Override
    public void transferred(long count) {
      transferredBytes.addAndGet(count);
    }

    @Override
    public long getTransferred() {
      return transferredBytes.get();
    }
  }

  enum TestFile {
    FILE_0_B(0),
    FILE_10_123_B(10_123),
    FILE_1_231_123_B(1_231_123),
    FILE_2_234_456_B(2_234_456),
    FILE_23_001_012_B(23_001_012),
    FILE_107_231_345_B(107_231_345);

    private final int size;

    TestFile(int size) {
      this.size = size;
    }

    public int getSize() {
      return size;
    }

    public String getName() {
      return "test-file-" + size + ".txt";
    }
  }
}
