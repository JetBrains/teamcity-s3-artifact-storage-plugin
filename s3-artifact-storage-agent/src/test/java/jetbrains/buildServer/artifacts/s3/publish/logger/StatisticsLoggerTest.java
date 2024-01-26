package jetbrains.buildServer.artifacts.s3.publish.logger;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import jetbrains.buildServer.artifacts.s3.publish.UploadStatistics;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

@Test
public class StatisticsLoggerTest {

  @Test
  public void logsASuccessfulUpload() {
    final StatisticsLogger logger = new StatisticsLogger();
    String uploadKey = "upload key";
    final Instant startTime = Instant.now();
    logger.uploadStarted(uploadKey, startTime, 1);
    logger.uploadFinished(uploadKey, startTime.plus(500, ChronoUnit.MILLIS), "sha");
    final UploadStatistics statistics = logger.getObjectStatistics(uploadKey);
    assertNotNull(statistics);
    assertEquals(statistics.getDuration(), Duration.ofMillis(500));
    assertTrue(statistics.isSuccessful());
  }

  @Test
  public void logsASuccessfulMultipartUpload() {
    final StatisticsLogger logger = new StatisticsLogger();
    String uploadKey = "upload key";
    final Instant overallStartTime = Instant.now();
    logger.uploadStarted(uploadKey, overallStartTime, 1);
    final int partUploadDurationInSeconds = 10;
    final Instant firstPartStartTime = Instant.now();
    final Instant secondPartStartTime = firstPartStartTime.plus(5, ChronoUnit.SECONDS);
    logger.partUploadStarted(uploadKey, firstPartStartTime, 0, 1);
    logger.partUploadStarted(uploadKey, secondPartStartTime, 1, 1);
    logger.partUploadFinished(uploadKey, firstPartStartTime.plus(partUploadDurationInSeconds, ChronoUnit.SECONDS), 0);
    logger.partUploadFinished(uploadKey, secondPartStartTime.plus(partUploadDurationInSeconds, ChronoUnit.SECONDS), 1);
    final Instant overallEndTime = overallStartTime.plus(1, ChronoUnit.MINUTES);
    logger.uploadFinished(uploadKey, overallEndTime, "sha");
    final UploadStatistics statistics = logger.getObjectStatistics(uploadKey);
    assertNotNull(statistics);
    assertEquals(statistics.getDuration(), Duration.ofSeconds(15));
    assertTrue(statistics.isSuccessful());
  }


  @Test
  public void doesntLogEndTimeForNonExistentUpload() {
    final StatisticsLogger logger = new StatisticsLogger();
    String uploadKey = "upload key";
    logger.uploadFinished(uploadKey, Instant.now(), "sha");
    final UploadStatistics statistics = logger.getObjectStatistics(uploadKey);
    assertNull(statistics);
  }

  @Test
  public void logsUploadError() {
    final StatisticsLogger logger = new StatisticsLogger();
    String uploadKey = "upload key";
    final Instant startTime = Instant.now();
    logger.uploadStarted(uploadKey, startTime, 1);
    logger.uploadFailed(uploadKey, "Error during upload", Instant.now());
    final UploadStatistics statistics = logger.getObjectStatistics(uploadKey);
    assertNotNull(statistics);
    assertTrue(statistics.hasErrors());
    assertEquals(statistics.getErrors().size(), 1);
    assertFalse(statistics.isSuccessful());
  }

  @Test
  public void logsMultipleUploadErrors() {
    final StatisticsLogger logger = new StatisticsLogger();
    String uploadKey = "upload key";
    final Instant startTime = Instant.now();
    logger.uploadStarted(uploadKey, startTime, 1);
    logger.uploadFailed(uploadKey, "Error during upload1", startTime.plusMillis(5));
    logger.uploadStarted(uploadKey, startTime.plusMillis(5), 1);
    logger.uploadFailed(uploadKey, "Error during upload2", startTime.plusMillis(10));
    logger.uploadStarted(uploadKey, startTime.plusMillis(10), 1);
    logger.uploadFailed(uploadKey, "Error during upload3", startTime.plusMillis(15));
    final UploadStatistics statistics = logger.getObjectStatistics(uploadKey);
    assertNotNull(statistics);
    assertTrue(statistics.hasErrors());
    assertEquals(statistics.getErrors().size(), 3);
    assertEquals(statistics.getDuration(), Duration.ofMillis(15));
    assertFalse(statistics.isSuccessful());
  }

  @Test
  public void logsMultipleUploadErrorsDuringMultipart() {
    final StatisticsLogger logger = new StatisticsLogger();
    String uploadKey = "upload key";
    final Instant overallStartTime = Instant.now();
    logger.uploadStarted(uploadKey, overallStartTime, 1);
    final int partUploadDurationInSeconds = 10;
    final Instant firstPartStartTime = Instant.now();
    final Instant secondPartStartTime = firstPartStartTime.plus(5, ChronoUnit.SECONDS);
    logger.partUploadStarted(uploadKey, firstPartStartTime, 0, 1);
    logger.partUploadStarted(uploadKey, secondPartStartTime, 1, 1);
    logger.partUploadFailed(uploadKey, "Error during upload of part 1", firstPartStartTime.plus(partUploadDurationInSeconds, ChronoUnit.SECONDS), 0);
    logger.partUploadFailed(uploadKey, "Error during upload of part 2", secondPartStartTime.plus(partUploadDurationInSeconds, ChronoUnit.SECONDS), 1);
    final Instant overallEndTime = overallStartTime.plus(1, ChronoUnit.MINUTES);
    logger.uploadFailed(uploadKey, "Error not during uploading of file chunks", overallEndTime);
    final UploadStatistics statistics = logger.getObjectStatistics(uploadKey);
    assertNotNull(statistics);
    assertTrue(statistics.hasErrors());
    assertEquals(statistics.getErrors().size(), 3);
    assertEquals(statistics.getDuration(), Duration.ofSeconds(15));
    assertFalse(statistics.isSuccessful());
  }


  @Test
  public void recordsSuccessEvenWithPreviousErrors() {
    final StatisticsLogger logger = new StatisticsLogger();
    String uploadKey = "upload key";
    final Instant startTime = Instant.now();
    logger.uploadStarted(uploadKey, startTime, 1);
    logger.uploadFailed(uploadKey, "Error during upload1", startTime.plusMillis(5));
    logger.uploadStarted(uploadKey, startTime.plusMillis(5), 1);
    logger.uploadFailed(uploadKey, "Error during upload2", startTime.plusMillis(10));
    logger.uploadStarted(uploadKey, startTime.plusMillis(10), 1);
    logger.uploadFinished(uploadKey, startTime.plusMillis(15), "sha");
    final UploadStatistics statistics = logger.getObjectStatistics(uploadKey);
    assertNotNull(statistics);
    assertTrue(statistics.hasErrors());
    assertEquals(statistics.getErrors().size(), 2);
    assertEquals(statistics.getDuration(), Duration.ofMillis(15));
    assertTrue(statistics.isSuccessful());
  }

  @Test
  public void recordsSuccessMultipartEvenWithPreviousErrors() {
    final StatisticsLogger logger = new StatisticsLogger();
    String uploadKey = "upload key";
    final Instant overallStartTime = Instant.now();
    logger.uploadStarted(uploadKey, overallStartTime, 1);
    final int partUploadDurationInSeconds = 10;
    // First attempt of upload with errors
    Instant firstPartStartTime = Instant.now();
    Instant secondPartStartTime = firstPartStartTime.plus(5, ChronoUnit.SECONDS);
    logger.partUploadStarted(uploadKey, firstPartStartTime, 0, 1);
    logger.partUploadStarted(uploadKey, secondPartStartTime, 1, 1);
    logger.partUploadFailed(uploadKey, "Error during upload of part 1", firstPartStartTime.plus(partUploadDurationInSeconds, ChronoUnit.SECONDS), 0);
    logger.partUploadFailed(uploadKey, "Error during upload of part 2", secondPartStartTime.plus(partUploadDurationInSeconds, ChronoUnit.SECONDS), 1);
    // Second attempt is successful
    firstPartStartTime = firstPartStartTime.plus(partUploadDurationInSeconds, ChronoUnit.SECONDS);
    secondPartStartTime = firstPartStartTime.plus(5, ChronoUnit.SECONDS);
    logger.partUploadStarted(uploadKey, firstPartStartTime, 0, 1);
    logger.partUploadStarted(uploadKey, secondPartStartTime, 1, 1);
    logger.partUploadFinished(uploadKey, firstPartStartTime.plus(partUploadDurationInSeconds, ChronoUnit.SECONDS), 0);
    logger.partUploadFinished(uploadKey, secondPartStartTime.plus(partUploadDurationInSeconds, ChronoUnit.SECONDS), 1);
    final Instant overallEndTime = overallStartTime.plus(1, ChronoUnit.MINUTES);
    logger.uploadFinished(uploadKey, overallEndTime, "sha");
    final UploadStatistics statistics = logger.getObjectStatistics(uploadKey);
    assertNotNull(statistics);
    assertTrue(statistics.hasErrors());
    assertEquals(statistics.getErrors().size(), 2);
    assertEquals(statistics.getDuration(), Duration.ofSeconds(15));
    assertTrue(statistics.isSuccessful());
  }

  @Test
  public void returnsAllErrors() {
    final StatisticsLogger logger = new StatisticsLogger();
    final Instant startTime = Instant.now();
    logger.uploadStarted("upload key1", startTime, 1);
    logger.uploadStarted("upload key2", startTime.plus(5, ChronoUnit.MILLIS), 1);
    logger.uploadStarted("upload key3", startTime.plus(10, ChronoUnit.MILLIS), 1);
    logger.uploadStarted("upload key4", startTime.plus(15, ChronoUnit.MILLIS), 1);
    logger.uploadFinished("upload key1", startTime.plus(5, ChronoUnit.MILLIS), "sha");
    logger.uploadFailed("upload key2", "error1", startTime.plus(10, ChronoUnit.MILLIS));
    logger.uploadFinished("upload key3", startTime.plus(15, ChronoUnit.MILLIS), "sha");
    logger.uploadFailed("upload key4", "error2", startTime.plus(20, ChronoUnit.MILLIS));
    Collection<UploadStatistics> statistics = logger.getAllRecords();
    assertNotNull(statistics);
    assertEquals(statistics.stream().filter(UploadStatistics::hasErrors).count(), 2);
  }
}
