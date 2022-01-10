package jetbrains.buildServer.artifacts.s3.publish.presigned.upload;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import jetbrains.buildServer.artifacts.s3.FileUploadInfo;
import jetbrains.buildServer.artifacts.s3.S3Configuration;
import jetbrains.buildServer.artifacts.s3.exceptions.FileUploadFailedException;
import jetbrains.buildServer.artifacts.s3.publish.logger.S3Log4jUploadLogger;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.LowLevelS3Client;
import jetbrains.buildServer.util.amazon.S3Util;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

@Test
public class S3SignedUrlFileUploaderTest {

  public void callsAllUploadsAndWaitsForCompletion() throws IOException {
    final AtomicInteger uploadCounter = new AtomicInteger(0);
    final S3PresignedUploadFactory factory = createUploadFactory(
      (artifactPath, objectKey, file, configuration, s3SignedUploadManager, lowLevelS3Client, progressListener) -> {
        return () -> {
          Thread.sleep(250);
          uploadCounter.incrementAndGet();
          return new FileUploadInfo(artifactPath, file.getAbsolutePath(), file.length(), "");
        };
      });

    final Supplier<PresignedUrlsProviderClient> clientSupplier = () -> Mockito.mock(PresignedUrlsProviderClient.class);
    final S3Configuration s3Configuration = new S3Configuration(new S3Util.S3AdvancedConfiguration(), new HashMap<>());
    final S3SignedUrlFileUploader uploader = new S3SignedUrlFileUploader(s3Configuration, new S3Log4jUploadLogger(), clientSupplier, factory);

    final File tempFile1 = Files.createTempFile("uploaderTest", "file").toFile();
    final File tempFile2 = Files.createTempFile("uploaderTest", "file").toFile();
    final File tempFile3 = Files.createTempFile("uploaderTest", "file").toFile();
    final File tempFile4 = Files.createTempFile("uploaderTest", "file").toFile();
    final File tempFile5 = Files.createTempFile("uploaderTest", "file").toFile();
    final Map<File, String> map = new HashMap<>();
    map.put(tempFile1, "");
    map.put(tempFile2, "");
    map.put(tempFile3, "");
    map.put(tempFile4, "");
    map.put(tempFile5, "");
    uploader.upload(map, () -> "", info -> {
    });
    assertEquals(uploadCounter.get(), 5);
  }


  public void providesCorrectUploadInfoToConsumer() throws IOException {
    final AtomicInteger uploadCounter = new AtomicInteger(0);
    final S3PresignedUploadFactory factory = createUploadFactory(
      (artifactPath, objectKey, file, configuration, s3SignedUploadManager, lowLevelS3Client, progressListener) -> {
        return () -> {
          uploadCounter.incrementAndGet();
          return new FileUploadInfo(artifactPath, file.getAbsolutePath(), file.length(), "");
        };
      }
    );

    final Supplier<PresignedUrlsProviderClient> clientSupplier = () -> Mockito.mock(PresignedUrlsProviderClient.class);
    final S3Configuration s3Configuration = new S3Configuration(new S3Util.S3AdvancedConfiguration(), new HashMap<>());
    final S3SignedUrlFileUploader uploader = new S3SignedUrlFileUploader(s3Configuration, new S3Log4jUploadLogger(), clientSupplier, factory);

    final File tempFile1 = Files.createTempFile("uploaderTest", "file").toFile();
    final File tempFile2 = Files.createTempFile("uploaderTest", "file").toFile();
    final File tempFile3 = Files.createTempFile("uploaderTest", "file").toFile();
    final File tempFile4 = Files.createTempFile("uploaderTest", "file").toFile();
    final File tempFile5 = Files.createTempFile("uploaderTest", "file").toFile();
    final Map<File, String> map = new HashMap<>();
    map.put(tempFile1, "");
    map.put(tempFile2, "");
    map.put(tempFile3, "");
    map.put(tempFile4, "");
    map.put(tempFile5, "");

    final List<String> uploadInfos = new CopyOnWriteArrayList<>();
    uploader.upload(map, () -> "", info -> {
      uploadInfos.add(info.getAbsolutePath());
    });
    assertEquals(uploadCounter.get(), 5);
    assertEquals(uploadInfos.stream().filter(Objects::nonNull).count(), 5);
  }

  public void stopsWhenUploadInterrupted() throws IOException {
    final AtomicInteger uploadCounter = new AtomicInteger(0);
    final S3PresignedUploadFactory factory = createUploadFactory(
      (artifactPath, objectKey, file, configuration, s3SignedUploadManager, lowLevelS3Client, progressListener) -> {
        return () -> {
          final int count = uploadCounter.getAndIncrement();
          if (count > 2) {
            throw new PublishingInterruptedException("testing interruption");
          }
          Thread.sleep(250);
          return new FileUploadInfo(artifactPath, file.getAbsolutePath(), file.length(), "");
        };
      }
    );

    final Supplier<PresignedUrlsProviderClient> clientSupplier = () -> Mockito.mock(PresignedUrlsProviderClient.class);
    final S3Configuration s3Configuration = new S3Configuration(new S3Util.S3AdvancedConfiguration().withNumberOfThreads(2), new HashMap<>());
    final S3SignedUrlFileUploader uploader = new S3SignedUrlFileUploader(s3Configuration, new S3Log4jUploadLogger(), clientSupplier, factory);

    final File tempFile1 = Files.createTempFile("uploaderTest", "file").toFile();
    final File tempFile2 = Files.createTempFile("uploaderTest", "file").toFile();
    final File tempFile3 = Files.createTempFile("uploaderTest", "file").toFile();
    final File tempFile4 = Files.createTempFile("uploaderTest", "file").toFile();
    final File tempFile5 = Files.createTempFile("uploaderTest", "file").toFile();
    final Map<File, String> map = new HashMap<>();
    map.put(tempFile1, "");
    map.put(tempFile2, "");
    map.put(tempFile3, "");
    map.put(tempFile4, "");
    map.put(tempFile5, "");
    final AtomicInteger infoCounter = new AtomicInteger(0);
    uploader.upload(map, () -> "", info -> {
      infoCounter.incrementAndGet();
    });
    assertEquals(uploadCounter.get(), 5);
    assertEquals(infoCounter.get(), 3);
  }

  public void retriesWhenUploadFailsWithRetriableException() throws IOException {
    final AtomicInteger uploadCounter = new AtomicInteger(0);
    final S3PresignedUploadFactory factory = createUploadFactory(
      (artifactPath, objectKey, file, configuration, s3SignedUploadManager, lowLevelS3Client, progressListener) -> {
        return () -> {
          if (uploadCounter.incrementAndGet() % 2 == 0) {
            throw new FileUploadFailedException("test", true);
          }
          return new FileUploadInfo(artifactPath, file.getAbsolutePath(), file.length(), "");
        };
      }
    );

    final Supplier<PresignedUrlsProviderClient> clientSupplier = () -> Mockito.mock(PresignedUrlsProviderClient.class);
    final S3Configuration s3Configuration = new S3Configuration(new S3Util.S3AdvancedConfiguration(), new HashMap<>());
    final S3SignedUrlFileUploader uploader = new S3SignedUrlFileUploader(s3Configuration, new S3Log4jUploadLogger(), clientSupplier, factory);

    final File tempFile1 = Files.createTempFile("uploaderTest", "file").toFile();
    final File tempFile2 = Files.createTempFile("uploaderTest", "file").toFile();
    final File tempFile3 = Files.createTempFile("uploaderTest", "file").toFile();
    final Map<File, String> map = new HashMap<>();
    map.put(tempFile1, "");
    map.put(tempFile2, "");
    map.put(tempFile3, "");
    uploader.upload(map, () -> "", info ->

    {
    });

    assertEquals(uploadCounter.get(), 5);
  }

  @Test
  public void failsWhenUploadFailsWithNonRetriableException() throws IOException {
    final AtomicInteger uploadCounter = new AtomicInteger(0);

    final S3PresignedUploadFactory factory = createUploadFactory(
      (artifactPath, objectKey, file, configuration, s3SignedUploadManager, lowLevelS3Client, progressListener) -> {
        return () -> {
          final int count = uploadCounter.getAndIncrement();
          if (count > 2) {
            throw new FileUploadFailedException("test", false);
          }
          return new FileUploadInfo(artifactPath, file.getAbsolutePath(), file.length(), "");
        };
      }
    );

    final Supplier<PresignedUrlsProviderClient> clientSupplier = () -> Mockito.mock(PresignedUrlsProviderClient.class);
    final S3Configuration s3Configuration = new S3Configuration(new S3Util.S3AdvancedConfiguration().withNumberOfThreads(2), new HashMap<>());
    final S3SignedUrlFileUploader uploader = new S3SignedUrlFileUploader(s3Configuration, new S3Log4jUploadLogger(), clientSupplier, factory);

    final File tempFile1 = Files.createTempFile("uploaderTest", "file1").toFile();
    final File tempFile2 = Files.createTempFile("uploaderTest", "file2").toFile();
    final File tempFile3 = Files.createTempFile("uploaderTest", "file3").toFile();
    final File tempFile4 = Files.createTempFile("uploaderTest", "file4").toFile();
    final File tempFile5 = Files.createTempFile("uploaderTest", "file5").toFile();
    final Map<File, String> map = new HashMap<>();
    map.put(tempFile1, "");
    map.put(tempFile2, "");
    map.put(tempFile3, "");
    map.put(tempFile4, "");
    map.put(tempFile5, "");
    final AtomicInteger infoCounter = new AtomicInteger();
    try {
      uploader.upload(map, () -> "", info -> {
        infoCounter.incrementAndGet();
      });
    } catch (FileUploadFailedException e) {
      assertEquals(uploadCounter.get(), 5);
      assertEquals(infoCounter.get(), 3);
      return;
    }
    Assert.fail();
  }


  public void completesOtherUploadsEvenIfOneTakesLonger() throws IOException {
    final AtomicInteger uploadCounter = new AtomicInteger(0);
    AtomicReference<String> longWaitingFilePath = new AtomicReference<>();
    final S3PresignedUploadFactory factory = createUploadFactory(
      (artifactPath, objectKey, file, configuration, s3SignedUploadManager, lowLevelS3Client, progressListener) -> {
        return () -> {
          final int counter = uploadCounter.getAndIncrement();
          if (counter == 2) {
            Thread.sleep(5000);
            longWaitingFilePath.set(file.getAbsolutePath());
          } else {
            Thread.sleep(250);
          }
          return new FileUploadInfo(artifactPath, file.getAbsolutePath(), file.length(), "");
        };
      });

    final Supplier<PresignedUrlsProviderClient> clientSupplier = () -> Mockito.mock(PresignedUrlsProviderClient.class);
    final S3Configuration s3Configuration = new S3Configuration(new S3Util.S3AdvancedConfiguration(), new HashMap<>());
    final S3SignedUrlFileUploader uploader = new S3SignedUrlFileUploader(s3Configuration, new S3Log4jUploadLogger(), clientSupplier, factory);

    final File tempFile1 = Files.createTempFile("uploaderTest", "file1").toFile();
    final File tempFile2 = Files.createTempFile("uploaderTest", "file2").toFile();
    final File tempFile3 = Files.createTempFile("uploaderTest", "file3").toFile();
    final File tempFile4 = Files.createTempFile("uploaderTest", "file4").toFile();
    final File tempFile5 = Files.createTempFile("uploaderTest", "file5").toFile();
    final Map<File, String> map = new HashMap<>();
    map.put(tempFile1, "");
    map.put(tempFile2, "");
    map.put(tempFile3, "");
    map.put(tempFile4, "");
    map.put(tempFile5, "");

    AtomicReference<Instant> uploadTimeLong = new AtomicReference<>();
    AtomicReference<Instant> uploadTimeOthers = new AtomicReference<>();
    uploader.upload(map, () -> "", info -> {
      if (info.getAbsolutePath().equals(longWaitingFilePath.get())) {
        uploadTimeLong.set(Instant.now());
      } else {
        uploadTimeOthers.set(Instant.now());
      }
    });
    assertEquals(uploadCounter.get(), 5);
    assertTrue(uploadTimeLong.get().isAfter(uploadTimeOthers.get()), "Long upload should not stop other uploads from completion");
  }


  private S3PresignedUploadFactory createUploadFactory(FactoryFunction function) {
    return new S3PresignedUploadFactory() {
      @NotNull
      @Override
      public Callable<FileUploadInfo> create(@NotNull String artifactPath,
                                             @NotNull String objectKey,
                                             @NotNull File file,
                                             @NotNull S3Util.S3AdvancedConfiguration configuration,
                                             @NotNull S3SignedUploadManager s3SignedUploadManager,
                                             @NotNull LowLevelS3Client lowLevelS3Client,
                                             @NotNull PresignedUploadProgressListener progressListener) {
        try {
          return function.apply(artifactPath, objectKey, file, configuration, s3SignedUploadManager, lowLevelS3Client, progressListener);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    };
  }

  @FunctionalInterface
  private interface FactoryFunction {
    Callable<FileUploadInfo> apply(@NotNull String artifactPath,
                                   @NotNull String objectKey,
                                   @NotNull File file,
                                   @NotNull S3Util.S3AdvancedConfiguration configuration,
                                   @NotNull S3SignedUploadManager s3SignedUploadManager,
                                   @NotNull LowLevelS3Client lowLevelS3Client,
                                   @NotNull PresignedUploadProgressListener progressListener) throws Exception;
  }
}
