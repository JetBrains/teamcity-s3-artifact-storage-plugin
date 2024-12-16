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
  public static Object[][] splitIntoPartsTestsData() {
    return new Object[][]{
      {
        1_000,
        100,
        10,
        1
      },
      {
        100,
        100_000,
        1_000,
        100
      },
      {
        10_000,
        100_000,
        1_000,
        100
      },
      {
        1_000_000,
        1000,
        100,
        2
      },
      {
        100_000_000,
        100_000,
        1_000,
        100
      },
      {
        10_000_000_000L,
        100_000,
        1_000,
        100
      },
      {
        1_000_000_000_000L,
        100_000,
        1_000,
        100
      },
      {
        100_000_000_000_000L,
        100_000,
        1_000,
        1_000_000
      },
      {
        100_000_000_000_000_000L,
        100_000,
        1_000,
        1_000_000
      },
      {
        100_000_000_000_000_000L,
        1_000_000_000,
        1_000,
        1_000_000
      },
    };
  }

  @Test(dataProvider = "splitIntoPartsTestsData")
  public void shouldSplitFileIntoPartsWitoutGapsAndKeepPartNumberEqualToPartIndexInList(long fileSize,
                                                                                        long minPartSize,
                                                                                        long minPartSizeLowerBound,
                                                                                        int maxThreads) {
    // arrange
    FileSplitterImpl splitter = createSplitter(minPartSize, minPartSizeLowerBound, maxThreads);

    // act
    List<FilePart> parts = splitter.split(fileSize);

    // assert
    int numberOfParts = parts.size();
    assertTrue(numberOfParts > 0);

    long expectedStartByte = 0L;
    for (int partIndex = 0; partIndex < numberOfParts; partIndex++) {
      FilePart part = parts.get(partIndex);
      long partNumber = part.getPartNumber();
      long startByte = part.getStartByte();
      long endByte = part.getEndByte();

      assertEquals(partNumber, partIndex);
      assertEquals(startByte, expectedStartByte);
      assertTrue(startByte < endByte);
      if (partIndex < numberOfParts - 1) {
        assertTrue(endByte < fileSize - 1);
      } else {
        assertEquals(endByte, fileSize - 1);
      }

      expectedStartByte = endByte + 1;
    }
  }

  @DataProvider
  public static Object[][] multiplePartsTestData() {
    return new Object[][]{
      {
        1_000_000,
        1000,
        100,
        2
      },
      {
        100_000_000,
        100_000,
        1_000,
        100
      },
      {
        10_000_000_000L,
        100_000,
        1_000,
        100
      },
      {
        1_000_000_000_000L,
        100_000,
        1_000,
        100
      },
      {
        100_000_000_000_000L,
        100_000,
        1_000,
        100
      },
    };
  }

  @Test(dataProvider = "multiplePartsTestData")
  public void shouldSplitIntoMultipleParts_whenFileIsLargeEnoughAndMaxThreadsIsMoreThanOne(long fileSize,
                                                                                           long minPartSize,
                                                                                           long minPartSizeLowerBound,
                                                                                           int maxThreads) {
    // arrange
    FileSplitterImpl splitter = createSplitter(minPartSize, minPartSizeLowerBound, maxThreads);

    // act
    SplitabilityReport splitabilityReport = splitter.testSplitability(fileSize);
    List<FilePart> parts = splitter.split(fileSize);

    // assert
    assertTrue(splitabilityReport.isSplittable());
    assertNull(splitabilityReport.getUnsplitablilityReason());
    assertTrue(parts.size() > 1);
  }

  @DataProvider
  public static Object[][] onePartTestData() {
    return new Object[][]{
      {
        10_000, // file is too small
        100_000,
        1_000,
        100
      },
      {
        1_000,
        100,
        10,
        1 // only one thread
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
  }

  @DataProvider
  public static Object[][] notPositiveFileSizeTestData() {
    return new Object[][]{
      {0},
      {-1_000_000}
    };
  }

  @Test(expectedExceptions = IllegalArgumentException.class, dataProvider = "notPositiveFileSizeTestData")
  public void shouldThrowException_whenFileSizeNotPositive(long fileSize) {
    // arrange
    FileSplitterImpl splitter = createSplitter(1000, 1000, 10);

    // act
    splitter.split(fileSize);
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
