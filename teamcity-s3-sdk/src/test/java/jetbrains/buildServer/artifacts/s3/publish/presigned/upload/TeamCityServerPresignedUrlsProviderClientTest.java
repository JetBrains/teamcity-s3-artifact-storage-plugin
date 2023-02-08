package jetbrains.buildServer.artifacts.s3.publish.presigned.upload;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.artifacts.s3.transport.MultipartUploadCompleteRequestDto;
import jetbrains.buildServer.xmlrpc.NodeIdHolder;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import static jetbrains.buildServer.artifacts.s3.S3Constants.ARTEFACTS_S3_UPLOAD_PRESIGN_URLS_HTML;
import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_ENABLE_MULTIPART_COMPLETION_RETRY;

@Test
public class TeamCityServerPresignedUrlsProviderClientTest extends BaseTestCase {


  public void retriesMultipartCompletionMessageOnConnectionErrorIfRetryEnabled() throws IOException {
    setInternalProperty(S3_ENABLE_MULTIPART_COMPLETION_RETRY, true);

    withMockServer(requestCount -> {
                     final NodeIdHolder nodeIdHolder = Mockito.mock(NodeIdHolder.class, Mockito.RETURNS_DEEP_STUBS);

                     final TeamCityConnectionConfiguration config = new TeamCityConnectionConfiguration(
                       "http://localhost:50644/",
                       ARTEFACTS_S3_UPLOAD_PRESIGN_URLS_HTML,
                       "testuser",
                       "testcode",
                       nodeIdHolder,
                       60 * 1000,
                       1, 0);
                     try {
                       final TeamCityServerPresignedUrlsProviderClient client = new TeamCityServerPresignedUrlsProviderClient(config, Collections.emptyList());
                       client.completeMultipartUpload(new MultipartUploadCompleteRequestDto("testobject", "uploadId", Collections.emptyList()));
                     } catch (RuntimeException e) {
                       assertTrue(requestCount.get() > 1);
                       assertEquals(SocketException.class, e.getCause().getCause().getClass());
                     }
                   }
    );
  }


  public void doesntRetryMultipartCompletionOnConnectionErrorIfRetryDisabled() throws IOException {
    setInternalProperty(S3_ENABLE_MULTIPART_COMPLETION_RETRY, false);

    withMockServer(requestCount -> {
                     final NodeIdHolder nodeIdHolder = Mockito.mock(NodeIdHolder.class, Mockito.RETURNS_DEEP_STUBS);

                     final TeamCityConnectionConfiguration config = new TeamCityConnectionConfiguration(
                       "http://localhost:50644/",
                       ARTEFACTS_S3_UPLOAD_PRESIGN_URLS_HTML,
                       "testuser",
                       "testcode",
                       nodeIdHolder,
                       60 * 1000,
                       5, 0);
                     try {
                       final TeamCityServerPresignedUrlsProviderClient client = new TeamCityServerPresignedUrlsProviderClient(config, Collections.emptyList());
                       client.completeMultipartUpload(new MultipartUploadCompleteRequestDto("testobject", "uploadId", Collections.emptyList()));
                     } catch (RuntimeException e) {
                       assertEquals(1, requestCount.get());
                       assertEquals(SocketException.class, e.getCause().getCause().getClass());
                     }
                   }
    );
  }

  private void withMockServer(Consumer<AtomicInteger> check) throws IOException {
    final ExecutorService executor = Executors.newSingleThreadExecutor();
    final AtomicInteger requestCount = new AtomicInteger(0);

    try (ServerSocket ss = new ServerSocket(50644)) {
      executor.submit(() -> {
        try {
          while (true) {
            Socket toReset = ss.accept();
            requestCount.incrementAndGet();
            toReset.close();
          }
        } catch (IOException e) {
          fail(e.getMessage());
        }
      });

      check.accept(requestCount);
    }
  }
}
