package jetbrains.buildServer.artifacts.s3.download;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.function.LongConsumer;
import jetbrains.buildServer.TempFiles;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

@Test
public class S3DownloadIOUtilTest {
  private static final Path JVM_WORKING_DIR = Paths.get(System.getProperty("user.dir"));
  private static final Path USER_HOME_DIR = Paths.get(System.getProperty("user.home"));

  private final TempFiles tempFiles = new TempFiles();
  private Path tempDir;

  @BeforeMethod
  public void setUp() throws IOException {
    tempDir = tempFiles.createTempDir().toPath();
  }

  @DataProvider
  public Object[][] getAbsoluteNormalizedPathTestData() {
    return new Object[][]{
      {USER_HOME_DIR.resolve("foo/bar"), USER_HOME_DIR.resolve("foo/bar")},
      {USER_HOME_DIR.resolve("foo/bar.tar"), USER_HOME_DIR.resolve("foo/bar.tar")},
      {USER_HOME_DIR.resolve("foo/bar/./baz/../../file/."), USER_HOME_DIR.resolve("foo/file")},
      {Paths.get("foo/bar/baz/../../file"), JVM_WORKING_DIR.resolve("foo/file")},
      {Paths.get("./foo/bar/baz/../../file"), JVM_WORKING_DIR.resolve("foo/file")},
      {Paths.get("aaa/./../foo/bar/baz/.."), JVM_WORKING_DIR.resolve("foo/bar")},
    };
  }

  @Test(dataProvider = "getAbsoluteNormalizedPathTestData")
  public void shouldReturnAbsoluteNormalizedPath(Path targetPath, Path expectedNormalizedPath) {
    // act
    Path normalizedPath = S3DownloadIOUtil.getAbsoluteNormalizedPath(targetPath);

    // assert
    assertEquals(normalizedPath, expectedNormalizedPath);
  }

  @DataProvider
  public Object[][] getUnfinishedFilePathTestData() {
    return new Object[][]{
      {USER_HOME_DIR.resolve("foo/bar"), USER_HOME_DIR.resolve("foo/bar.unfinished")},
      {USER_HOME_DIR.resolve("foo/bar.tar"), USER_HOME_DIR.resolve("foo/bar.tar.unfinished")},
      {USER_HOME_DIR.resolve("foo/bar/./baz/../../file/."), USER_HOME_DIR.resolve("foo/file.unfinished")},
      {Paths.get("foo/bar/baz/../../file"), JVM_WORKING_DIR.resolve("foo/file.unfinished")},
      {Paths.get("./foo/bar/baz/../../file"), JVM_WORKING_DIR.resolve("foo/file.unfinished")},
      {Paths.get("aaa/./../foo/bar/baz/.."), JVM_WORKING_DIR.resolve("foo/bar.unfinished")},
    };
  }

  @Test(dataProvider = "getUnfinishedFilePathTestData")
  public void shouldReturnUnfinishedFilePath(Path file, Path expectedUnfinishedFilePath) {
    // act
    Path unfinishedFilePath = S3DownloadIOUtil.getUnfinishedFilePath(file);

    // assert
    assertEquals(unfinishedFilePath, expectedUnfinishedFilePath);
  }

  @DataProvider
  public Object[][] getFilePartPathTestData() {
    return new Object[][]{
      {USER_HOME_DIR.resolve("foo/bar"), 0, "bar.part.0"},
      {USER_HOME_DIR.resolve("foo/bar.tar"), 1, "bar.tar.part.1"},
      {USER_HOME_DIR.resolve("foo/bar/./baz/../../file/."), 17, "file.part.17"},
      {Paths.get("foo/bar/baz/../../file"), 10, "file.part.10"},
      {Paths.get("./foo/bar/baz/../../file"), 0, "file.part.0"},
      {Paths.get("aaa/./../foo/bar/baz/.."), 100000, "bar.part.100000"},
    };
  }

  @Test(dataProvider = "getFilePartPathTestData")
  public void shouldReturnFilePartPath(Path file, int partNumber, String expectedFilePartPath) {
    // act
    Path filePartPath = S3DownloadIOUtil.getFilePartPath(file, partNumber, tempDir);

    // assert
    assertEquals(filePartPath, tempDir.resolve(expectedFilePartPath));
  }

  @Test
  public void shouldCreateDirectoryHierarchyWhenNotExists() throws IOException {
    // arrange
    Path intermediateDir = tempDir.resolve("intermediateDir");
    Path targetDir = intermediateDir.resolve("targetDir");
    List<Path> allLevels = Arrays.asList(intermediateDir, targetDir);
    allLevels.forEach(level -> assertFalse(Files.exists(level)));

    // act
    S3DownloadIOUtil.ensureDirectoryExists(targetDir);

    // assert
    allLevels.forEach(level -> assertTrue(Files.isDirectory(level)));
  }

  @Test
  public void shouldNotDoAnythingWhenDirectoryHierarchyExists() throws IOException {
    // arrange
    Path intermediateDir = tempDir.resolve("intermediateDir");
    Path targetDir = intermediateDir.resolve("targetDir");
    List<Path> allLevels = Arrays.asList(intermediateDir, targetDir);
    allLevels.forEach(level -> assertFalse(Files.exists(level)));
    for (Path level : allLevels) {
      Files.createDirectory(level);
    }
    allLevels.forEach(level -> assertTrue(Files.isDirectory(level)));

    // act
    S3DownloadIOUtil.ensureDirectoryExists(targetDir);

    // assert
    allLevels.forEach(level -> assertTrue(Files.isDirectory(level)));
  }

  @Test(expectedExceptions = IOException.class)
  public void shouldThrowExceptionWhenDirectoryHierarchyCanNotBeCreated() throws IOException {
    // arrange
    Path existingFile = tempDir.resolve("existingFile");
    Path targetDir = existingFile.resolve("targetDir");
    List<Path> allLevels = Arrays.asList(existingFile, targetDir);
    allLevels.forEach(level -> assertFalse(Files.exists(level)));
    Files.createFile(existingFile);
    assertTrue(Files.isRegularFile(existingFile));

    // act
    S3DownloadIOUtil.ensureDirectoryExists(targetDir);
  }

  @Test(expectedExceptions = IOException.class)
  public void shouldThrowExceptionWhenDirectoryCanNotBeCreated() throws IOException {
    // arrange
    Path intermediateDir = tempDir.resolve("intermediateDir");
    Path target = intermediateDir.resolve("target");
    List<Path> allLevels = Arrays.asList(intermediateDir, target);
    allLevels.forEach(level -> assertFalse(Files.exists(level)));
    Files.createFile(target);
    assertTrue(Files.isRegularFile(target));

    // act
    S3DownloadIOUtil.ensureDirectoryExists(target);
  }

  @Test
  public void shouldReserveBytesWhenFileNotExists() throws IOException {
    // arrange
    Path targetFile = tempDir.resolve("targetFile");
    assertFalse(Files.exists(targetFile));
    int expectedBytes = 5_334_234;

    // act
    S3DownloadIOUtil.reserveFileBytes(targetFile, expectedBytes);

    // assert
    assertTrue(Files.isRegularFile(targetFile));
    assertEquals(Files.size(targetFile), expectedBytes);
  }

  @DataProvider
  public Object[][] reserveFileBytesFileExistsTestData() {
    return new Object[][]{
      {2_234}, // fewer
      {5_334_234}, // equal
      {12_534_893}, // more
    };
  }

  @Test(dataProvider = "reserveFileBytesFileExistsTestData")
  public void shouldReserveBytesWhenFileExists(int existingFileSize) throws IOException {
    // arrange
    Path targetFile = tempDir.resolve("targetFile");
    assertFalse(Files.exists(targetFile));
    int expectedBytes = 5_334_234;
    Files.write(targetFile, new byte[existingFileSize]);
    assertEquals(Files.size(targetFile), existingFileSize);

    // act
    S3DownloadIOUtil.reserveFileBytes(targetFile, expectedBytes);

    // assert
    assertTrue(Files.isRegularFile(targetFile));
    assertEquals(Files.size(targetFile), expectedBytes);
  }

  @Test(expectedExceptions = IOException.class)
  public void shouldThrowExceptionWhenReservingBytesOnADirectory() throws IOException {
    // arrange
    Path target = tempDir.resolve("target");
    assertFalse(Files.exists(target));
    Files.createDirectory(target);
    assertTrue(Files.isDirectory(target));

    // act
    S3DownloadIOUtil.reserveFileBytes(target, 5_334_234);
  }

  @Test(expectedExceptions = IOException.class)
  public void shouldThrowExceptionWhenReservingBytesAndParentDirectoryDoesNotExist() throws IOException {
    // arrange
    Path parent = tempDir.resolve("parent");
    Path targetFile = parent.resolve("targetFile");
    assertFalse(Files.exists(parent));
    assertFalse(Files.exists(targetFile));

    // act
    S3DownloadIOUtil.reserveFileBytes(targetFile, 5_334_234);
  }

  @DataProvider
  public Object[][] reserveFileBytesNotPositiveNumberTestData() {
    return new Object[][]{
      {-123},
      {0},
    };
  }

  @Test(expectedExceptions = IllegalArgumentException.class, dataProvider = "reserveFileBytesNotPositiveNumberTestData")
  public void shouldThrowExceptionWhenReservingNotPositiveNumberOfBytes(int bytes) throws IOException {
    // arrange
    Path targetFile = tempDir.resolve("targetFile");
    assertFalse(Files.exists(targetFile));

    // act
    S3DownloadIOUtil.reserveFileBytes(targetFile, bytes);
  }

  @DataProvider
  public Object[][] transferExpectedAndAllBytesTestData() {
    return new Object[][]{
      {1_873}, // fewer data than bufer size
      {11_111}, // data size equals buffer size
      {123_321}, // more data than buffer size
      {51_123_321}, // signifacantly more data (~50 MB)
      {0}, // empty channel
    };
  }

  @Test(dataProvider = "transferExpectedAndAllBytesTestData")
  public void shouldTransferExpectedBytes(int dataSize) throws IOException {
    // arrange
    int bufferSize = 11_111;
    IORunnable interruptedCheck = mock(IORunnable.class);
    LongConsumer progressTracker = mock(LongConsumer.class);
    ArgumentCaptor<Long> progressCaptor = ArgumentCaptor.forClass(Long.class);
    ByteArrayOutputStream dataReciever = new ByteArrayOutputStream();
    byte[] data = generateData(dataSize);

    // act
    try (ReadableByteChannel sourceChannel = Channels.newChannel(new ByteArrayInputStream(data));
         WritableByteChannel targetChannel = Channels.newChannel(dataReciever)) {
      S3DownloadIOUtil.transferExpectedBytes(sourceChannel, targetChannel, dataSize, bufferSize, interruptedCheck, progressTracker);
    }

    // assert
    assertEquals(dataReciever.toByteArray(), data);
    verify(interruptedCheck, atLeast(1)).run();
    verify(progressTracker, atLeast(0)).accept(progressCaptor.capture());
    long bytesTransferredReported = progressCaptor.getAllValues()
      .stream()
      .reduce(0L, (accumulated, current) -> accumulated + current);

    assertEquals(bytesTransferredReported, data.length);
  }

  @Test(dataProvider = "transferExpectedAndAllBytesTestData")
  public void shouldTransferAllBytes(int dataSize) throws IOException {
    // arrange
    int bufferSize = 11_111;
    IORunnable interruptedCheck = mock(IORunnable.class);
    LongConsumer progressTracker = mock(LongConsumer.class);
    ArgumentCaptor<Long> progressCaptor = ArgumentCaptor.forClass(Long.class);
    ByteArrayOutputStream dataReciever = new ByteArrayOutputStream();
    byte[] data = generateData(dataSize);

    // act
    try (ReadableByteChannel sourceChannel = Channels.newChannel(new ByteArrayInputStream(data));
         WritableByteChannel targetChannel = Channels.newChannel(dataReciever)) {
      S3DownloadIOUtil.transferAllBytes(sourceChannel, targetChannel, bufferSize, interruptedCheck, progressTracker);
    }

    // assert
    assertEquals(dataReciever.toByteArray(), data);
    verify(interruptedCheck, atLeast(1)).run();
    verify(progressTracker, atLeast(0)).accept(progressCaptor.capture());
    long bytesTransferredReported = progressCaptor.getAllValues()
      .stream()
      .reduce(0L, (accumulated, current) -> accumulated + current);

    assertEquals(bytesTransferredReported, data.length);
  }

  @DataProvider
  public Object[][] transferExpectedBytesDifferentFromExpectedTestData() {
    return new Object[][]{
      {10_001}, // more bytes than expected
      {9_999}, // fewer bytes than expected
    };
  }

  @Test(expectedExceptions = IOException.class, dataProvider = "transferExpectedBytesDifferentFromExpectedTestData")
  public void shouldThrowExceptionWhenTransferringDifferentNumberOfBytesThanExpected(int dataSize) throws IOException {
    // arrange
    ByteArrayOutputStream dataReciever = new ByteArrayOutputStream();
    byte[] data = generateData(dataSize);

    // act
    try (ReadableByteChannel sourceChannel = Channels.newChannel(new ByteArrayInputStream(data));
         WritableByteChannel targetChannel = Channels.newChannel(dataReciever)) {
      S3DownloadIOUtil.transferExpectedBytes(sourceChannel, targetChannel, 10_000, 1_000, () -> {}, l -> {});
    }
  }

  @DataProvider
  public Object[][] transferExpectedAndAllBytesWrongBufferSizeTestData() {
    return new Object[][]{
      {0},
      {-1},
      {-10_000},
    };
  }

  @Test(expectedExceptions = IllegalArgumentException.class, dataProvider = "transferExpectedAndAllBytesWrongBufferSizeTestData")
  public void shouldThrowExceptionWhenTransferringExpectedBytesAndBufferIsNotPositive(int bufferSize) throws IOException {
    // arrange
    ByteArrayOutputStream dataReciever = new ByteArrayOutputStream();
    byte[] data = generateData(10_000);

    // act
    try (ReadableByteChannel sourceChannel = Channels.newChannel(new ByteArrayInputStream(data));
         WritableByteChannel targetChannel = Channels.newChannel(dataReciever)) {
      S3DownloadIOUtil.transferExpectedBytes(sourceChannel, targetChannel, 10_000, bufferSize, () -> {}, l -> {});
    }
  }

  @Test(expectedExceptions = IllegalArgumentException.class, dataProvider = "transferExpectedAndAllBytesWrongBufferSizeTestData")
  public void shouldThrowExceptionWhenTransferringAllBytesAndBufferIsNotPositive(int bufferSize) throws IOException {
    // arrange
    ByteArrayOutputStream dataReciever = new ByteArrayOutputStream();
    byte[] data = generateData(10_000);

    // act
    try (ReadableByteChannel sourceChannel = Channels.newChannel(new ByteArrayInputStream(data));
         WritableByteChannel targetChannel = Channels.newChannel(dataReciever)) {
      S3DownloadIOUtil.transferAllBytes(sourceChannel, targetChannel, bufferSize, () -> {}, l -> {});
    }
  }

  @DataProvider
  public Object[][] transferExpectedFileBytesTestData() {
    return new Object[][]{
      {
        12_876,
        0,
        0,
      },
      {
        12_876,
        345, // source position changed
        0,
      },
      {
        12_876,
        0,
        1234, // target position changed
      },
      {
        12_876,
        345, // source position changed
        1234, // target position changed
      },
      {
        12_876,
        0,
        34_123, // target position beyond data size
      },
      {
        51_123_321, // ~50 MB data
        345,
        1234,
      },
      {
        0, // empty file
        345,
        1234,
      },
    };
  }

  @Test(dataProvider = "transferExpectedFileBytesTestData")
  public void shouldTransferExpectedFileBytes(int dataSize, int sourcePosition, int targetPosition) throws IOException {
    // arrange
    IORunnable interruptedCheck = mock(IORunnable.class);
    LongConsumer progressTracker = mock(LongConsumer.class);
    ArgumentCaptor<Long> progressCaptor = ArgumentCaptor.forClass(Long.class);
    byte[] data = generateData(dataSize);

    Path sourceFile = tempDir.resolve("sourceFile.txt");
    assertFalse(Files.exists(sourceFile));
    if (sourcePosition > 0) {
      byte[] dummyPrefix = new byte[sourcePosition];
      Files.write(sourceFile, dummyPrefix);
    }
    Files.write(sourceFile, data, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    assertTrue(Files.exists(sourceFile));
    assertEquals(Files.size(sourceFile),  sourcePosition + data.length);

    Path targetFile = tempDir.resolve("targetFile.txt");
    assertFalse(Files.exists(targetFile));

    // act
    try (FileChannel sourceChannel = FileChannel.open(sourceFile, StandardOpenOption.READ);
         FileChannel targetChannel = FileChannel.open(targetFile, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
      sourceChannel.position(sourcePosition);
      targetChannel.position(targetPosition);
      S3DownloadIOUtil.transferExpectedFileBytes(sourceChannel, targetChannel, dataSize, interruptedCheck, progressTracker);
    }

    // assert
    assertTrue(Files.exists(targetFile));
    byte[] targetContent = Files.readAllBytes(targetFile);
    byte[] writtenDataRange = dataSize > 0 ? Arrays.copyOfRange(targetContent, targetPosition, targetPosition + dataSize) : new byte[]{};
    assertEquals(writtenDataRange, data);

    verify(interruptedCheck, atLeast(1)).run();
    verify(progressTracker, atLeast(0)).accept(progressCaptor.capture());
    long bytesTransferredReported = progressCaptor.getAllValues()
      .stream()
      .reduce(0L, (accumulated, current) -> accumulated + current);

    assertEquals(bytesTransferredReported, dataSize);
  }

  @Test(expectedExceptions = IOException.class, dataProvider = "transferExpectedBytesDifferentFromExpectedTestData")
  public void shouldThrowExceptionWhenTransferringDifferentNumberOfFileBytesThanExpected(int dataSize) throws IOException {
    // arrange
    int expectedBytes = 10_000;
    IORunnable interruptedCheck = mock(IORunnable.class);
    LongConsumer progressTracker = mock(LongConsumer.class);
    byte[] data = generateData(dataSize);

    Path sourceFile = tempDir.resolve("sourceFile.txt");
    assertFalse(Files.exists(sourceFile));
    Files.write(sourceFile, data, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    assertTrue(Files.exists(sourceFile));

    Path targetFile = tempDir.resolve("targetFile.txt");
    assertFalse(Files.exists(targetFile));

    // act
    try (FileChannel sourceChannel = FileChannel.open(sourceFile, StandardOpenOption.READ);
         FileChannel targetChannel = FileChannel.open(targetFile, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
      S3DownloadIOUtil.transferExpectedFileBytes(sourceChannel, targetChannel, expectedBytes, interruptedCheck, progressTracker);
    }
  }

  @Test(expectedExceptions = IOException.class)
  public void shouldThrowExceptionWhenInterruptedWhenTransferringExpectedBytes() throws IOException {
    // arrange
    IORunnable interruptedCheck = () -> {
      throw new IOException();
    };

    // act
    try (ReadableByteChannel sourceChannel = Channels.newChannel(new ByteArrayInputStream(generateData(100)));
         WritableByteChannel targetChannel = Channels.newChannel(new ByteArrayOutputStream())) {
      S3DownloadIOUtil.transferExpectedBytes(sourceChannel, targetChannel, 100, 100, interruptedCheck, l -> {});
    }
  }

  @Test(expectedExceptions = IOException.class)
  public void shouldThrowExceptionWhenInterruptedWhenTransferringAllBytes() throws IOException {
    // arrange
    IORunnable interruptedCheck = () -> {
      throw new IOException();
    };

    // act
    try (ReadableByteChannel sourceChannel = Channels.newChannel(new ByteArrayInputStream(generateData(100)));
         WritableByteChannel targetChannel = Channels.newChannel(new ByteArrayOutputStream())) {
      S3DownloadIOUtil.transferAllBytes(sourceChannel, targetChannel, 100, interruptedCheck, l -> {});
    }
  }

  @Test(expectedExceptions = IOException.class)
  public void shouldThrowExceptionWhenInterruptedWhenTransferringExpectedFileBytes() throws IOException {
    // arrange
    IORunnable interruptedCheck = () -> {
      throw new IOException();
    };

    byte[] data = generateData(100);

    Path sourceFile = tempDir.resolve("sourceFile.txt");
    assertFalse(Files.exists(sourceFile));
    Files.write(sourceFile, data, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    assertTrue(Files.exists(sourceFile));
    assertEquals(Files.size(sourceFile),  data.length);

    Path targetFile = tempDir.resolve("targetFile.txt");
    assertFalse(Files.exists(targetFile));

    // act
    try (FileChannel sourceChannel = FileChannel.open(sourceFile, StandardOpenOption.READ);
         FileChannel targetChannel = FileChannel.open(targetFile, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
      S3DownloadIOUtil.transferExpectedFileBytes(sourceChannel, targetChannel, 100, interruptedCheck, l -> {});
    }
  }

  private static byte[] generateData(int dataSizeBytes) {
    byte[] data = new byte[dataSizeBytes];
    for (int i = 0; i < dataSizeBytes; i++) {
      data[i] = (byte)i;
    }

    return data;
  }

  @AfterMethod
  public void tearDown() {
    tempFiles.cleanup();
  }
}
