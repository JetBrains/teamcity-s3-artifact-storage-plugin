package jetbrains.buildServer.artifacts.s3.publish.presigned.upload;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.HttpClientUtil;
import jetbrains.buildServer.artifacts.s3.transport.MultipartUploadCompleteRequestDto;
import jetbrains.buildServer.artifacts.s3.transport.PresignedUrlListRequestDto;
import jetbrains.buildServer.artifacts.s3.transport.PresignedUrlRequestDto;
import jetbrains.buildServer.artifacts.s3.transport.PresignedUrlRequestSerializer;
import jetbrains.buildServer.http.HttpUserAgent;
import jetbrains.buildServer.util.SimpleHttpServer;
import jetbrains.buildServer.xmlrpc.NodeIdHolder;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.artifacts.s3.S3Constants.*;

@Test
public class TeamCityServerPresignedUrlsProviderClientTest extends BaseTestCase {

  public static final String OBJECT_KEY = "testobject";
  public static final String FAKE_DIGEST = "fakeDigest";
  public static final String UPLOAD_ID = "uploadId";
  private SimpleUploadServer mySimpleHttpServer;

  @BeforeMethod(alwaysRun = true)
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    mySimpleHttpServer = new SimpleUploadServer();
    mySimpleHttpServer.start();
  }

  @AfterMethod
  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    mySimpleHttpServer.stop();
  }

  @NotNull
  private String getTeamCityUrl(SimpleHttpServer server) {
    return "http://localhost:" + server.getPort();
  }

  @NotNull
  private String getTeamCityUrl() {
    return getTeamCityUrl(mySimpleHttpServer);
  }

  public void testMultipartCompletion() {
    final NodeIdHolder nodeIdHolder = Mockito.mock(NodeIdHolder.class, Mockito.RETURNS_DEEP_STUBS);

    final TeamCityConnectionConfiguration config = new TeamCityConnectionConfiguration(
      getTeamCityUrl(),
      ARTEFACTS_S3_UPLOAD_PRESIGN_URLS_HTML,
      "testuser",
      "testcode",
      nodeIdHolder,
      60 * 1000,
      1, 0);
    try {
      final TeamCityServerPresignedUrlsProviderClient client = new TeamCityServerPresignedUrlsProviderClient(config, Collections.emptyList());
      client.completeMultipartUpload(new MultipartUploadCompleteRequestDto(OBJECT_KEY, UPLOAD_ID, Collections.emptyList()));
    } catch (RuntimeException e) {
      assertEquals(HttpClientUtil.HttpErrorCodeException.class, e.getCause().getCause().getClass());
      final String request = mySimpleHttpServer.myRequests.get(0);
      final String requestBody = mySimpleHttpServer.myRequestBodies.get(0);
      assertNotNull(request);
      assertNotNull(requestBody);
      assertContains(request, "POST /httpAuth" + ARTEFACTS_S3_UPLOAD_PRESIGN_URLS_HTML);
      assertContains(request, "User-Agent: " + HttpUserAgent.getUserAgent());
      assertContains(requestBody, "Content-Disposition: form-data; name=\"s3-object-key\"" +
                                  "\r\n\r\n" +
                                  "testobject");
      assertContains(requestBody, "Content-Disposition: form-data; name=\"s3-object-key_BASE64\"" +
                                  "\r\n\r\n" +
                                  "dGVzdG9iamVjdA==");
      assertContains(requestBody, "Content-Disposition: form-data; name=\"s3-multipart-upload-completed\"" +
                                  "\r\n\r\n" +
                                  "uploadId");
      assertContains(requestBody, "Content-Disposition: form-data; name=\"s3-multipart-upload-successful\"" +
                                  "\r\n\r\n" +
                                  "true");
    }
  }

  public void retriesMultipartCompletionMessageOnConnectionErrorIfRetryEnabled() {
    setInternalProperty(S3_ENABLE_MULTIPART_COMPLETION_RETRY, true);

    final NodeIdHolder nodeIdHolder = Mockito.mock(NodeIdHolder.class, Mockito.RETURNS_DEEP_STUBS);

    final TeamCityConnectionConfiguration config = new TeamCityConnectionConfiguration(
      getTeamCityUrl(),
      ARTEFACTS_S3_UPLOAD_PRESIGN_URLS_HTML,
      "testuser",
      "testcode",
      nodeIdHolder,
      60 * 1000,
      1, 0);
    try {
      final TeamCityServerPresignedUrlsProviderClient client = new TeamCityServerPresignedUrlsProviderClient(config, Collections.emptyList());
      client.completeMultipartUpload(new MultipartUploadCompleteRequestDto(OBJECT_KEY, UPLOAD_ID, Collections.emptyList()));
    } catch (RuntimeException e) {
      assertTrue(mySimpleHttpServer.myRequests.size() > 1);
      return;
    }

    fail("Must throw exception");
  }

  public void retriesMultipartCompletionMessageOnLackOfResponseIfRetryEnabled() throws IOException {
    setInternalProperty(S3_ENABLE_MULTIPART_COMPLETION_RETRY, true);

    final AtomicInteger numRequests = new AtomicInteger(0);
    final SimpleHttpServer nonResponsiveServer = new SimpleHttpServer() {
      @Override
      protected Response getResponse(String request) {
        numRequests.incrementAndGet();
        try {
          Thread.sleep(10 * 1000);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
        throw new RuntimeException("Error");
      }
    };
    nonResponsiveServer.start();

    final NodeIdHolder nodeIdHolder = Mockito.mock(NodeIdHolder.class, Mockito.RETURNS_DEEP_STUBS);

    final TeamCityConnectionConfiguration config = new TeamCityConnectionConfiguration(
      getTeamCityUrl(nonResponsiveServer),
      ARTEFACTS_S3_UPLOAD_PRESIGN_URLS_HTML,
      "testuser",
      "testcode",
      nodeIdHolder,
      1 * 1000,
      1, 0);
    try {
      final TeamCityServerPresignedUrlsProviderClient client = new TeamCityServerPresignedUrlsProviderClient(config, Collections.emptyList());
      client.completeMultipartUpload(new MultipartUploadCompleteRequestDto(OBJECT_KEY, UPLOAD_ID, Collections.emptyList()));
    } catch (RuntimeException e) {
      assertTrue(numRequests.get() > 1);
    }

    nonResponsiveServer.stop();
    myTestLogger.clearFailure();
  }


  public void doesntRetryMultipartCompletionOnConnectionErrorIfRetryDisabled() {
    setInternalProperty(S3_ENABLE_MULTIPART_COMPLETION_RETRY, false);

    final NodeIdHolder nodeIdHolder = Mockito.mock(NodeIdHolder.class, Mockito.RETURNS_DEEP_STUBS);

    final TeamCityConnectionConfiguration config = new TeamCityConnectionConfiguration(
      getTeamCityUrl(),
      ARTEFACTS_S3_UPLOAD_PRESIGN_URLS_HTML,
      "testuser",
      "testcode",
      nodeIdHolder,
      60 * 1000,
      5, 0);
    try {
      final TeamCityServerPresignedUrlsProviderClient client = new TeamCityServerPresignedUrlsProviderClient(config, Collections.emptyList());
      client.completeMultipartUpload(new MultipartUploadCompleteRequestDto(OBJECT_KEY, UPLOAD_ID, Collections.emptyList()));
    } catch (RuntimeException e) {
      assertEquals(1, mySimpleHttpServer.myRequests.size());
    }
  }

  public void testGetMultipartPresignedUrl() {
    final NodeIdHolder nodeIdHolder = Mockito.mock(NodeIdHolder.class, Mockito.RETURNS_DEEP_STUBS);

    final TeamCityConnectionConfiguration config = new TeamCityConnectionConfiguration(
      getTeamCityUrl(),
      ARTEFACTS_S3_UPLOAD_PRESIGN_URLS_HTML,
      "testuser",
      "testcode",
      nodeIdHolder,
      60 * 1000,
      1, 0);
    try {
      final TeamCityServerPresignedUrlsProviderClient client = new TeamCityServerPresignedUrlsProviderClient(config, Collections.emptyList());
      client.getMultipartPresignedUrl(OBJECT_KEY, Collections.singletonList(FAKE_DIGEST), UPLOAD_ID, 1000L);
    } catch (RuntimeException e) {
      final String request = mySimpleHttpServer.myRequests.get(0);
      final String requestBody = mySimpleHttpServer.myRequestBodies.get(0);
      assertNotNull(request);
      assertContains(request, "POST /httpAuth" + ARTEFACTS_S3_UPLOAD_PRESIGN_URLS_HTML);

      final PresignedUrlListRequestDto dto = PresignedUrlRequestSerializer.deserializeRequest(requestBody);
      assertEquals(Long.valueOf(1000), dto.getCustomTtl());
      assertEquals(1, dto.getPresignedUrlRequests().size());
      PresignedUrlRequestDto presignedUrlRequestDto = new ArrayList<>(dto.getPresignedUrlRequests()).get(0);
      assertEquals(OBJECT_KEY, presignedUrlRequestDto.getObjectKey());
      assertEquals(1, presignedUrlRequestDto.getNumberOfParts());
      assertEquals(Collections.singletonList(FAKE_DIGEST), presignedUrlRequestDto.getDigests());
      assertContains(request, "User-Agent: " + HttpUserAgent.getUserAgent());
      assertContains(request, S3_ARTIFACT_KEYS_HEADER_NAME + ": testobject");
    }
  }

  private class SimpleUploadServer extends SimpleHttpServer {
    final List<String> myRequests = new ArrayList<>();
    final List<String> myRequestBodies = new ArrayList<>();

    @Override
    protected Response getResponse(String request) {
      myRequests.add(request);
      return createStringResponse(STATUS_LINE_500, Collections.emptyList(), "");
    }

    // THis actually gives you the request body, so we're gonna not ignore it
    @Override
    protected void postProcessSocketData(String httpHeader, @NotNull InputStream is) throws IOException {
      StringBuilder result = new StringBuilder();
      while (is.available() > 0) {
        int c = is.read();
        if (c == -1) break;
        result.append((char)c);
      }
      myRequestBodies.add(result.toString());
    }
  }
}
