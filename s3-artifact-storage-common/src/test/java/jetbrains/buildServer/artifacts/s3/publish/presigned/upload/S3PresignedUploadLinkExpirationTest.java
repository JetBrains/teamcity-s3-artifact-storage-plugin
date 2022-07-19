package jetbrains.buildServer.artifacts.s3.publish.presigned.upload;

import com.intellij.openapi.util.Pair;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.artifacts.s3.exceptions.FileUploadFailedException;
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

public class S3PresignedUploadLinkExpirationTest extends BaseTestCase {

  @Test
  public void repeatsUploadWithDifferentTtlWhenFirstRequestExpired() throws IOException {
    final S3SignedUploadManager uploadManager = Mockito.mock(S3SignedUploadManager.class, Answers.RETURNS_DEEP_STUBS);
    Mockito.when(uploadManager.getUrlWithDigest(anyString(), any())).thenReturn(Pair.create("url", "digest"));

    final LowLevelS3Client s3client = Mockito.mock(LowLevelS3Client.class, Answers.RETURNS_DEEP_STUBS);
    Mockito.when(s3client.uploadFile(anyString(), any(), anyString()))
           .thenThrow(new HttpClientUtil.HttpErrorCodeException(403, "Request has expired", true))
           .thenReturn("digest");

    final PresignedUploadProgressListener listener = Mockito.mock(PresignedUploadProgressListener.class, Answers.RETURNS_DEEP_STUBS);

    final S3Util.S3AdvancedConfiguration configuration = new S3Util.S3AdvancedConfiguration();
    final File file = Files.createTempFile("s3uploadTest", "file").toFile();
    final S3PresignedUpload upload = new S3PresignedUpload("testpath", "key", file, configuration, uploadManager, s3client, listener);
    try {
      upload.call();

    } catch (FileUploadFailedException e) {
      try {
        Mockito.verify(uploadManager, Mockito.times(1)).getUrlWithDigest("key", null);
        assertTrue(e.isRecoverable());
        upload.call();
        Mockito.verify(uploadManager, Mockito.times(1)).getUrlWithDigest("key", S3Util.DEFAULT_URL_LIFETIME_SEC * 2L);
        return;
      } catch (FileUploadFailedException ex) {
        fail("Should only fail once");
      }
    }
    fail("Should throw a FileUploadFailedException");
  }

  @Test
  public void repeatsUploadWithSameTtlWhenErrorIsRepeatable() throws IOException {
    final S3SignedUploadManager uploadManager = Mockito.mock(S3SignedUploadManager.class, Answers.RETURNS_DEEP_STUBS);
    Mockito.when(uploadManager.getUrlWithDigest(anyString(), any())).thenReturn(Pair.create("url", "digest"));

    final LowLevelS3Client s3client = Mockito.mock(LowLevelS3Client.class, Answers.RETURNS_DEEP_STUBS);
    Mockito.when(s3client.uploadFile(anyString(), any(), anyString()))
           .thenThrow(new HttpClientUtil.HttpErrorCodeException(403, "Unrelated auth exception", true))
           .thenReturn("digest");

    final PresignedUploadProgressListener listener = Mockito.mock(PresignedUploadProgressListener.class, Answers.RETURNS_DEEP_STUBS);

    final S3Util.S3AdvancedConfiguration configuration = new S3Util.S3AdvancedConfiguration();
    final File file = Files.createTempFile("s3uploadTest", "file").toFile();
    final S3PresignedUpload upload = new S3PresignedUpload("testpath", "key", file, configuration, uploadManager, s3client, listener);
    try {
      upload.call();

    } catch (FileUploadFailedException e) {
      try {
        assertTrue(e.isRecoverable());
        upload.call();
        Mockito.verify(uploadManager, Mockito.times(2)).getUrlWithDigest("key", null);
        return;
      } catch (FileUploadFailedException ex) {
        fail("Should only fail once");
      }
    }
    fail("Should throw a FileUploadFailedException");
  }

  @Test
  public void failsWhenErrorIsNotRepeatable() throws IOException {
    final S3SignedUploadManager uploadManager = Mockito.mock(S3SignedUploadManager.class, Answers.RETURNS_DEEP_STUBS);
    Mockito.when(uploadManager.getUrlWithDigest(anyString(), any())).thenReturn(Pair.create("url", "digest"));

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
  public void repeatsMultipartUploadWithDifferentTtlWhenFirstRequestExpired() throws IOException {
    final S3SignedUploadManager uploadManager = Mockito.mock(S3SignedUploadManager.class);
    final PresignedUrlDto multipartDto = PresignedUrlDto.multiPart(
      "key",
      "uploadId",
      Arrays.asList(new PresignedUrlPartDto("url", 1),
                    new PresignedUrlPartDto("url", 2),
                    new PresignedUrlPartDto("url", 3)
      ));
    Mockito.when(uploadManager.getMultipartUploadUrls("key", Arrays.asList(null, null, null), null, null))
           .thenReturn(multipartDto);

    Mockito.when(uploadManager.getMultipartUploadUrls("key", Arrays.asList(null, null, null), "uploadId", S3Util.DEFAULT_URL_LIFETIME_SEC * 2L))
           .thenReturn(multipartDto);

    final LowLevelS3Client s3client = Mockito.mock(LowLevelS3Client.class, Answers.RETURNS_DEEP_STUBS);
    Mockito.when(s3client.uploadFilePart(anyString(), any()))
           .thenReturn(Hex.encodeHexString("etag".getBytes(StandardCharsets.UTF_8)))
           .thenThrow(new HttpClientUtil.HttpErrorCodeException(403, "Request has expired", true))
           .thenReturn(Hex.encodeHexString("etag".getBytes(StandardCharsets.UTF_8)));

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
      try {
        Mockito.verify(uploadManager, Mockito.times(1)).getMultipartUploadUrls("key", Arrays.asList(null, null, null), null, null);
        assertTrue(e.isRecoverable());
        upload.call();
        Mockito.verify(uploadManager, Mockito.times(1)).getMultipartUploadUrls("key", Arrays.asList(null, null, null), "uploadId", S3Util.DEFAULT_URL_LIFETIME_SEC * 2L);
        return;
      } catch (FileUploadFailedException ex) {
        fail("Should only fail once");
      }
    }
    fail("Should throw a FileUploadFailedException");
  }

  @Test
  public void failsMultipartUploadWhenErrorIsNotRepeatable() throws IOException {
    final S3SignedUploadManager uploadManager = Mockito.mock(S3SignedUploadManager.class);
    final PresignedUrlDto multipartDto = PresignedUrlDto.multiPart(
      "key",
      "uploadId",
      Arrays.asList(new PresignedUrlPartDto("url", 1),
                    new PresignedUrlPartDto("url", 2),
                    new PresignedUrlPartDto("url", 3)
      ));
    Mockito.when(uploadManager.getMultipartUploadUrls("key", Arrays.asList(null, null, null), null, null))
           .thenReturn(multipartDto);

    final LowLevelS3Client s3client = Mockito.mock(LowLevelS3Client.class, Answers.RETURNS_DEEP_STUBS);
    Mockito.when(s3client.uploadFilePart(anyString(), any()))
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

}
