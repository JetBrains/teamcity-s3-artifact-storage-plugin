package jetbrains.buildServer.artifacts.s3.download.parallel;

import java.util.List;
import jetbrains.buildServer.artifacts.s3.download.S3DownloadConfiguration;
import jetbrains.buildServer.artifacts.s3.download.parallel.splitter.SplitabilityReport;
import jetbrains.buildServer.artifacts.s3.download.parallel.splitter.impl.FileSplitterImpl;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.*;

@Test
public class FileSplitterImplTest {

  @DataProvider
  public static Object[][] multiplePartsTestData() {
    return new Object[][]{
      // 2 parts: partsSize >= minPartSize + minPartSizeLowerBound and maxThreads is 2
      {
        1_234_567,
        1_987,
        111,
        2,
        2
      },
      // 2 parts, left bound: partsSize = minPartSize + minPartSizeLowerBound
      {
        123_123,
        122_123,
        1_000,
        123,
        2
      },
      // 2 parts, right bound for non-extended 2nd part: partsSize = 2 * minPartSize
      {
        222_222_222,
        111_111_111,
        1_000,
        123,
        2
      },
      //  2 parts, left bound for extended 2nd part: partSize = 2 * minPartSize + 1
      {
        222_222_223,
        111_111_111,
        1_000,
        123,
        2
      },
      // 2 parts, right bound: fileSize = 2 * minPartSize + minPartSizeLowerBound - 1
      {
        222_223_221,
        111_111_111,
        1_000,
        123,
        2
      },
      // 3 parts, left bound: fileSize = 2 * minPartSize + minPartSizeLowerBound
      {
        222_223_222,
        111_111_111,
        1_000,
        123,
        3
      },
      // right bound for unstretched parts: fileSize = maxThreads * minPartSize
      {
        555_555_555,
        111_111_111,
        1_000,
        5,
        5
      },
      // left bound for stretched parts: fileSize = maxThreads * minPartSize + 1
      {
        555_555_556,
        111_111_111,
        1_000,
        5,
        5
      },
      // significantly stretched parts: fileSize >> maxThreads * minPartSize
      {
        123_463_857_456_234L,
        111_111_111,
        1_000,
        15,
        15
      },
    };
  }

  @Test(dataProvider = "multiplePartsTestData")
  public void shouldSplitIntoMultipleParts_whenFileIsLargeEnoughAndMaxThreadsIsMoreThanOne(long fileSize,
                                                                                           long minPartSize,
                                                                                           long minPartSizeLowerBound,
                                                                                           int maxThreads,
                                                                                           int expectedPartCount) {
    // arrange
    FileSplitterImpl splitter = createSplitter(minPartSize, minPartSizeLowerBound, maxThreads);

    // act
    SplitabilityReport splitabilityReport = splitter.testSplitability(fileSize);
    List<FilePart> parts = splitter.split(fileSize);

    // assert
    assertTrue(splitabilityReport.isSplittable());
    assertNull(splitabilityReport.getUnsplitablilityReason());
    assertEquals(parts.size(), expectedPartCount);
    assertPartsCoverFileWithoutGapsAndPartNumberEqualsPartIndexInList(fileSize, parts);
  }

  @DataProvider
  public static Object[][] onePartTestData() {
    return new Object[][]{
      // 1 part, left bound (min posistive file size)
      {
        1,
        123_783,
        1_000,
        321
      },
      // 1 part, right bound: fileSize = minPartSize + minPartSizeLowerBound - 1
      {
        124_782,
        123_783,
        1_000,
        543
      },
      // 1 part: file is large enough fileSize > minPartSize + minPartSizeLowerBound but only one thread
      {
        123_224_782,
        12_132,
        15,
        1
      },
    };
  }

  @Test(dataProvider = "onePartTestData")
  public void shouldSplitIntoOnePart_whenFileIsTooSmallOrMaxThreadsIsOne(long fileSize,
                                                                         long minPartSize,
                                                                         long minPartSizeLowerBound,
                                                                         int maxThreads) {
    // arrange
    FileSplitterImpl splitter = createSplitter(minPartSize, minPartSizeLowerBound, maxThreads);

    // act
    SplitabilityReport splitabilityReport = splitter.testSplitability(fileSize);
    List<FilePart> parts = splitter.split(fileSize);

    // assert
    assertFalse(splitabilityReport.isSplittable());
    assertNotNull(splitabilityReport.getUnsplitablilityReason());
    assertEquals(parts.size(), 1);
    assertPartsCoverFileWithoutGapsAndPartNumberEqualsPartIndexInList(fileSize, parts);
  }

  @DataProvider
  public static Object[][] notPositiveFileSizeTestData() {
    return new Object[][]{
      {0},
      {-123_123_321}
    };
  }

  @Test(expectedExceptions = IllegalArgumentException.class, dataProvider = "notPositiveFileSizeTestData")
  public void shouldThrowException_whenFileSizeNotPositive(long fileSize) {
    // arrange
    FileSplitterImpl splitter = createSplitter(1000, 1000, 10);

    // act
    splitter.split(fileSize);
  }

  private static void assertPartsCoverFileWithoutGapsAndPartNumberEqualsPartIndexInList(long fileSize, @NotNull List<FilePart> actualParts) {
    int partCount = actualParts.size();
    assertTrue(partCount > 0);

    long expectedStartByte = 0L;
    for (int partIndex = 0; partIndex < partCount; partIndex++) {
      FilePart part = actualParts.get(partIndex);
      long partNumber = part.getPartNumber();
      long startByte = part.getStartByte();
      long endByte = part.getEndByte();

      assertEquals(partNumber, partIndex);
      assertEquals(startByte, expectedStartByte);
      assertTrue(startByte <= endByte);
      if (partIndex < partCount - 1) {
        assertTrue(endByte < fileSize - 1);
      } else {
        assertEquals(endByte, fileSize - 1);
      }

      expectedStartByte = endByte + 1;
    }
  }

  @NotNull
  private static FileSplitterImpl createSplitter(long minPartSize, long minPartSizeLowerBound, int maxThreads) {
    S3DownloadConfiguration configurationMock = mock(S3DownloadConfiguration.class);
    when(configurationMock.getMinPartSizeBytes()).thenReturn(minPartSize);
    when(configurationMock.getMinPartSizeLowerBoundBytes()).thenReturn(minPartSizeLowerBound);
    when(configurationMock.getMaxThreads()).thenReturn(maxThreads);
    return new FileSplitterImpl(configurationMock);
  }
}
