package jetbrains.buildServer.artifacts.s3.publish.presigned.upload;

import com.intellij.openapi.util.Pair;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.SocketException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.artifacts.s3.exceptions.FileUploadFailedException;
import jetbrains.buildServer.artifacts.s3.publish.presigned.upload.S3SignedUploadManager.SignedUrlInfo;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.HttpClientUtil;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.LowLevelS3Client;
import jetbrains.buildServer.artifacts.s3.transport.PresignedUrlDto;
import jetbrains.buildServer.artifacts.s3.transport.PresignedUrlPartDto;
import jetbrains.buildServer.util.amazon.S3Util;
import org.apache.commons.codec.binary.Hex;
import org.mockito.Answers;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;

public class S3PresignedUploadTest extends BaseTestCase {

  @Test
  public void repeatsUploadWithDifferentTtlWhenFirstRequestExpired() throws IOException, URISyntaxException {
    final S3SignedUploadManager uploadManager = Mockito.mock(S3SignedUploadManager.class, Answers.RETURNS_DEEP_STUBS);
    Mockito.when(uploadManager.getUrlWithDigest(anyString(), any())).thenReturn(new SignedUrlInfo("url", "digest", 1L));

    final LowLevelS3Client s3client = Mockito.mock(LowLevelS3Client.class, Answers.RETURNS_DEEP_STUBS);
    Mockito.when(s3client.uploadFile(anyString(), any(), anyString()))
           .thenThrow(new HttpClientUtil.HttpErrorCodeException(403, "Request has expired", false))
           .thenReturn(CompletableFuture.completedFuture("digest"));

    final PresignedUploadProgressListener listener = Mockito.mock(PresignedUploadProgressListener.class, Answers.RETURNS_DEEP_STUBS);

    final S3Util.S3AdvancedConfiguration configuration = new S3Util.S3AdvancedConfiguration();
    final File file = Files.createTempFile("s3uploadTest", "file").toFile();
    final S3PresignedUpload upload = new S3PresignedUpload("testpath", "key", file, configuration, uploadManager, s3client, listener);
    try {
      upload.call();

    } catch (FileUploadFailedException e) {
      try {
        Mockito.verify(uploadManager, times(1)).getUrlWithDigest("key", null);
        assertFalse(e.isRecoverable());
        upload.call();
        Mockito.verify(uploadManager, times(1)).getUrlWithDigest("key", S3Util.DEFAULT_URL_LIFETIME_SEC * 2L);
        return;
      } catch (FileUploadFailedException ex) {
        fail("Should only fail once");
      }
    }
    fail("Should throw a FileUploadFailedException");
  }

  @Test
  public void repeatsUploadWithSameTtlWhenErrorIsRepeatable() throws IOException, URISyntaxException {
    final S3SignedUploadManager uploadManager = Mockito.mock(S3SignedUploadManager.class, Answers.RETURNS_DEEP_STUBS);
    Mockito.when(uploadManager.getUrlWithDigest(anyString(), any())).thenReturn(new SignedUrlInfo("url", "digest", 1L));

    final LowLevelS3Client s3client = Mockito.mock(LowLevelS3Client.class, Answers.RETURNS_DEEP_STUBS);
    Mockito.when(s3client.uploadFile(anyString(), any(), anyString()))
           .thenThrow(new HttpClientUtil.HttpErrorCodeException(403, "Unrelated auth exception retriable", true))
           .thenThrow(new HttpClientUtil.HttpErrorCodeException(403, "Unrelated auth exception failure", false))
           // Note: this return is necessary for second upload.call() to succeed
           .thenReturn(CompletableFuture.completedFuture("digest"));

    final PresignedUploadProgressListener listener = Mockito.mock(PresignedUploadProgressListener.class, Answers.RETURNS_DEEP_STUBS);

    final S3Util.S3AdvancedConfiguration configuration = new S3Util.S3AdvancedConfiguration();
    final File file = Files.createTempFile("s3uploadTest", "file").toFile();
    final S3PresignedUpload upload = new S3PresignedUpload("testpath", "key", file, configuration, uploadManager, s3client, listener);
    try {
      upload.call();

    } catch (FileUploadFailedException e) {
      try {
        assertFalse(e.isRecoverable());
        upload.call();
        Mockito.verify(uploadManager, times(2)).getUrlWithDigest("key", null);
        return;
      } catch (FileUploadFailedException ex) {
        fail("Should only fail once");
      }
    }
    fail("Should throw a FileUploadFailedException");
  }

  @Test
  public void failsWhenErrorIsNotRepeatable() throws IOException, URISyntaxException {
    final S3SignedUploadManager uploadManager = Mockito.mock(S3SignedUploadManager.class, Answers.RETURNS_DEEP_STUBS);
    Mockito.when(uploadManager.getUrlWithDigest(anyString(), any())).thenReturn(new SignedUrlInfo("url", "digest", 1L));

    final LowLevelS3Client s3client = Mockito.mock(LowLevelS3Client.class, Answers.RETURNS_DEEP_STUBS);
    Mockito.when(s3client.uploadFile(anyString(), any(), anyString())).thenThrow(new HttpClientUtil.HttpErrorCodeException(503, "exception", false));

    final PresignedUploadProgressListener listener = Mockito.mock(PresignedUploadProgressListener.class, Answers.RETURNS_DEEP_STUBS);

    final S3Util.S3AdvancedConfiguration configuration = new S3Util.S3AdvancedConfiguration();
    final File file = Files.createTempFile("s3uploadTest", "file").toFile();
    final S3PresignedUpload upload = new S3PresignedUpload("testpath", "key", file, configuration, uploadManager, s3client, listener);
    try {
      upload.call();

    } catch (FileUploadFailedException e) {
      assertFalse(e.isRecoverable());
      return;
    }
    fail("Should throw a FileUploadFailedException");
  }

  @Test
  public void repeatsMultipartUploadWithDifferentTtlWhenFirstRequestExpired() throws IOException, URISyntaxException {
    final S3SignedUploadManager uploadManager = Mockito.mock(S3SignedUploadManager.class);
    final PresignedUrlDto multipartDto = PresignedUrlDto.multiPart(
      "key",
      "uploadId",
      Arrays.asList(new PresignedUrlPartDto("url", 1),
                    new PresignedUrlPartDto("url", 2),
                    new PresignedUrlPartDto("url", 3)
      ));
    Mockito.when(uploadManager.getMultipartUploadUrls("key", Arrays.asList(null, null, null), null, null))
           .thenReturn(Pair.create(multipartDto, 1L));

    Mockito.when(uploadManager.getMultipartUploadUrls("key", Arrays.asList(null, null, null), "uploadId", S3Util.DEFAULT_URL_LIFETIME_SEC * 2L))
           .thenReturn(Pair.create(multipartDto, 1L));

    final LowLevelS3Client s3client = Mockito.mock(LowLevelS3Client.class, Answers.RETURNS_DEEP_STUBS);
    final CompletableFuture<String> failedFuture = new CompletableFuture<>();
    failedFuture.completeExceptionally(new HttpClientUtil.HttpErrorCodeException(403, "Request has expired", false));
    Mockito.when(s3client.uploadFilePart(anyString(), any()))
           .thenReturn(
             CompletableFuture.completedFuture(Hex.encodeHexString("etag".getBytes(StandardCharsets.UTF_8))))
           .thenReturn(failedFuture)
           .thenReturn(
             CompletableFuture.completedFuture(Hex.encodeHexString("etag".getBytes(StandardCharsets.UTF_8))));

    final PresignedUploadProgressListener listener = Mockito.mock(PresignedUploadProgressListener.class, Answers.RETURNS_DEEP_STUBS);

    final S3Util.S3AdvancedConfiguration configuration = new S3Util.S3AdvancedConfiguration().withConsistencyCheckEnabled(false);
    final File file = Files.createTempFile("s3uploadTest", "file").toFile();
    try (final RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw")) {
      randomAccessFile.setLength(12 * 1024 * 1024);
    }
    final S3PresignedMultipartUpload upload = new S3PresignedMultipartUpload("testpath", "key", file, configuration, uploadManager, s3client, listener);
    try {
      upload.call();
      // FileUploadFailedException will be thrown only when we encounter a non-recoverable error
    } catch (FileUploadFailedException e) {
      try {
        Mockito.verify(uploadManager, times(1)).getMultipartUploadUrls("key", Arrays.asList(null, null, null), null, null);
        assertFalse(e.isRecoverable());
        upload.call();
        Mockito.verify(uploadManager, times(1)).getMultipartUploadUrls("key", Arrays.asList(null, null, null), "uploadId", S3Util.DEFAULT_URL_LIFETIME_SEC * 2L);
        return;
      } catch (FileUploadFailedException ex) {
        fail("Should only fail once");
      }
    }
    fail("Should throw a FileUploadFailedException");
  }

  @Test
  public void failsMultipartUploadWhenErrorIsNotRepeatable() throws IOException, URISyntaxException {
    final S3SignedUploadManager uploadManager = Mockito.mock(S3SignedUploadManager.class);
    final PresignedUrlDto multipartDto = PresignedUrlDto.multiPart(
      "key",
      "uploadId",
      Arrays.asList(new PresignedUrlPartDto("url", 1),
                    new PresignedUrlPartDto("url", 2),
                    new PresignedUrlPartDto("url", 3)
      ));
    Mockito.when(uploadManager.getMultipartUploadUrls("key", Arrays.asList(null, null, null), null, null))
           .thenReturn(Pair.create(multipartDto, 1L));

    final LowLevelS3Client s3client = Mockito.mock(LowLevelS3Client.class, Answers.RETURNS_DEEP_STUBS);
    Mockito.when(s3client.uploadFilePart(anyString(), any()))
           // Note: this throw will prevent instaniation of the CompletableFuture, hense finish upload with an exceptional case without retry possibility verification
           .thenThrow(new HttpClientUtil.HttpErrorCodeException(503, "Nonrecoverable request", false));

    final PresignedUploadProgressListener listener = Mockito.mock(PresignedUploadProgressListener.class, Answers.RETURNS_DEEP_STUBS);

    final S3Util.S3AdvancedConfiguration configuration = new S3Util.S3AdvancedConfiguration().withConsistencyCheckEnabled(false);
    final File file = Files.createTempFile("s3uploadTest", "file").toFile();
    try (final RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw")) {
      randomAccessFile.setLength(12 * 1024 * 1024);
    }
    final S3PresignedMultipartUpload upload = new S3PresignedMultipartUpload("testpath", "key", file, configuration, uploadManager, s3client, listener);
    try {
      upload.call();

    } catch (FileUploadFailedException e) {
      assertFalse(e.isRecoverable());
      return;
    }
    fail("Should throw a FileUploadFailedException");
  }

  @Test
  public void doesNotFailWhenFileNotFound() throws URISyntaxException {
    final S3SignedUploadManager uploadManager = Mockito.mock(S3SignedUploadManager.class, Answers.RETURNS_DEEP_STUBS);
    Mockito.when(uploadManager.getUrlWithDigest(anyString(), any())).thenReturn(new SignedUrlInfo("url", "digest", 1L));

    final LowLevelS3Client s3client = Mockito.mock(LowLevelS3Client.class, Answers.RETURNS_DEEP_STUBS);
    Mockito.when(s3client.uploadFile(anyString(), any(), anyString()))
           .thenThrow(new HttpClientUtil.HttpErrorCodeException(403, "Request has expired", true))
           .thenReturn(CompletableFuture.completedFuture("digest"));

    final PresignedUploadProgressListener listener = Mockito.mock(PresignedUploadProgressListener.class, Answers.RETURNS_DEEP_STUBS);

    final S3Util.S3AdvancedConfiguration configuration = new S3Util.S3AdvancedConfiguration();
    final File file = new File("s3uploadTest", "file");
    final S3PresignedUpload upload = new S3PresignedUpload("testpath", "key", file, configuration, uploadManager, s3client, listener);
    try {
      upload.call();
    } catch (FileUploadFailedException e) {
      fail("Should not throw a FileUploadFailedException");
    }

    Mockito.verify(s3client, times(0)).uploadFile(any(), any(), any());
  }

  @Test
  public void doesNotFailWhenMultipartTtlFail() throws URISyntaxException, IOException {
    final S3SignedUploadManager uploadManager = Mockito.mock(S3SignedUploadManager.class, Answers.RETURNS_DEEP_STUBS);
    final PresignedUrlDto multipartDto = PresignedUrlDto.multiPart(
      "objectKey",
      "uploadId",
      Arrays.asList(new PresignedUrlPartDto("url", 1),
                    new PresignedUrlPartDto("url", 2),
                    new PresignedUrlPartDto("url", 3)
      ));
    Mockito.when(uploadManager.getMultipartUploadUrls(anyString(), any(), any(), any()))
           .thenReturn(Pair.create(multipartDto, 1L));

    final CompletableFuture<String> errorCompletableFuture = new CompletableFuture<>();
    errorCompletableFuture.completeExceptionally(new SocketException("Broken pipe"));
    final LowLevelS3Client s3client = Mockito.mock(LowLevelS3Client.class, Answers.RETURNS_DEEP_STUBS);
    Mockito.when(s3client.uploadFilePart(anyString(), any()))
           .thenReturn(CompletableFuture.completedFuture(Hex.encodeHexString("etag1".getBytes())))
           .thenReturn(CompletableFuture.completedFuture(Hex.encodeHexString("etag2".getBytes())))
           .thenReturn(errorCompletableFuture)
           .thenReturn(CompletableFuture.completedFuture(Hex.encodeHexString("etag3".getBytes())));

    final PresignedUploadProgressListener listener = Mockito.mock(PresignedUploadProgressListener.class, Answers.RETURNS_DEEP_STUBS);

    final S3Util.S3AdvancedConfiguration configuration = new S3Util.S3AdvancedConfiguration().withConsistencyCheckEnabled(false);
    final File file = Files.createTempFile("s3uploadTest", "file").toFile();
    try (final RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw")) {
      randomAccessFile.setLength(12 * 1024 * 1024);
    }
    final S3PresignedMultipartUpload upload = new S3PresignedMultipartUpload("testpath", "key", file, configuration, uploadManager, s3client, listener);

    try {
      upload.call();
    } catch (RuntimeException e) {
      assertTrue(e.getCause() instanceof SocketException);
      // retry upload
      upload.call();
      return;
    }

    fail("Should throw a RuntimeException");
  }

  @Test
  public void failUploadAndRestartItFromStart() throws URISyntaxException, IOException {
    final S3SignedUploadManager uploadManager = Mockito.mock(S3SignedUploadManager.class, Answers.RETURNS_DEEP_STUBS);
    final PresignedUrlDto multipartDto = PresignedUrlDto.multiPart(
      "objectKey",
      "uploadId",
      Arrays.asList(new PresignedUrlPartDto("url", 1),
                    new PresignedUrlPartDto("url", 2),
                    new PresignedUrlPartDto("url", 3)
      ));
    Mockito.when(uploadManager.getMultipartUploadUrls(anyString(), any(), any(), any()))
           .thenReturn(Pair.create(multipartDto, 1L));

    final CompletableFuture<String> errorCompletableFuture = new CompletableFuture<>();
    errorCompletableFuture.completeExceptionally(new SocketException());
    final CompletableFuture<String> failureCompletableFuture = new CompletableFuture<>();
    failureCompletableFuture.completeExceptionally(new RuntimeException("Critical failure"));
    final LowLevelS3Client s3client = Mockito.mock(LowLevelS3Client.class, Answers.RETURNS_DEEP_STUBS);
    Mockito.when(s3client.uploadFilePart(anyString(), any()))
           .thenReturn(CompletableFuture.completedFuture(Hex.encodeHexString("etag1".getBytes())))
           .thenReturn(CompletableFuture.completedFuture(Hex.encodeHexString("etag2".getBytes())))
           .thenReturn(errorCompletableFuture)
           .thenReturn(errorCompletableFuture)
           .thenReturn(errorCompletableFuture)
           .thenReturn(errorCompletableFuture)
           .thenReturn(failureCompletableFuture)
           .thenReturn(CompletableFuture.completedFuture(Hex.encodeHexString("etag1".getBytes())))
           .thenReturn(CompletableFuture.completedFuture(Hex.encodeHexString("etag2".getBytes())))
           .thenReturn(CompletableFuture.completedFuture(Hex.encodeHexString("etag3".getBytes())));

    final PresignedUploadProgressListener listener = Mockito.mock(PresignedUploadProgressListener.class, Answers.RETURNS_DEEP_STUBS);

    final S3Util.S3AdvancedConfiguration configuration = new S3Util.S3AdvancedConfiguration().withConsistencyCheckEnabled(false);
    final File file = Files.createTempFile("s3uploadTest", "file").toFile();
    try (final RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw")) {
      randomAccessFile.setLength(12 * 1024 * 1024);
    }
    final S3PresignedMultipartUpload upload = new S3PresignedMultipartUpload("testpath", "key", file, configuration, uploadManager, s3client, listener);

    try {
      upload.call();
    } catch (RuntimeException e) {
      assertEquals("Critical failure", e.getMessage());
      // retry upload
      upload.call();
      return;
    }

    fail("Should throw a RuntimeException");
  }
}
