package jetbrains.buildServer.artifacts.s3.publish.logger;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

@Test
public class StatisticsLoggerTest {

  @Test
  public void logsASuccessfulUpload() {
    final StatisticsLogger logger = new StatisticsLogger();
    String uploadKey = "upload key";
    final Instant startTime = Instant.now();
    logger.uploadStarted(uploadKey, startTime);
    logger.uploadFinished(uploadKey, startTime.plus(500, ChronoUnit.MILLIS));
    final StatisticsLogger.UploadStatistics statistics = logger.getObjectStatistics(uploadKey);
    assertNotNull(statistics);
    assertEquals(statistics.getDuration(), Duration.ofMillis(500));
    assertTrue(statistics.isSuccessful());
  }

  @Test
  public void logsASuccessfulMultipartUpload() {
    final StatisticsLogger logger = new StatisticsLogger();
    String uploadKey = "upload key";
    final Instant startTime = Instant.now();
    logger.uploadStarted(uploadKey, startTime);
    logger.uploadStarted(uploadKey, startTime);
    logger.uploadFinished(uploadKey, startTime.plus(5, ChronoUnit.MILLIS));
    logger.uploadStarted(uploadKey, startTime.plus(10, ChronoUnit.MILLIS));
    logger.uploadFinished(uploadKey, startTime.plus(15, ChronoUnit.MILLIS));
    logger.uploadStarted(uploadKey, startTime.plus(20, ChronoUnit.MILLIS));
    logger.uploadFinished(uploadKey, startTime.plus(25, ChronoUnit.MILLIS));
    logger.uploadFinished(uploadKey, startTime.plus(25, ChronoUnit.MILLIS));
    final StatisticsLogger.UploadStatistics statistics = logger.getObjectStatistics(uploadKey);
    assertNotNull(statistics);
    assertEquals(statistics.getDuration(), Duration.ofMillis(15));
    assertTrue(statistics.isSuccessful());
  }


  @Test
  public void doesntLogEndTimeForNonExistentUpload() {
    final StatisticsLogger logger = new StatisticsLogger();
    String uploadKey = "upload key";
    logger.uploadFinished(uploadKey, Instant.now());
    final StatisticsLogger.UploadStatistics statistics = logger.getObjectStatistics(uploadKey);
    assertNull(statistics);
  }

  @Test
  public void logsUploadError() {
    final StatisticsLogger logger = new StatisticsLogger();
    String uploadKey = "upload key";
    final Instant startTime = Instant.now();
    logger.uploadStarted(uploadKey, startTime);
    logger.uploadFailed(uploadKey, "Error during upload", Instant.now());
    final StatisticsLogger.UploadStatistics statistics = logger.getObjectStatistics(uploadKey);
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
    logger.uploadStarted(uploadKey, startTime);
    logger.uploadFailed(uploadKey, "Error during upload1", startTime.plusMillis(5));
    logger.uploadStarted(uploadKey, startTime.plusMillis(5));
    logger.uploadFailed(uploadKey, "Error during upload2", startTime.plusMillis(10));
    logger.uploadStarted(uploadKey, startTime.plusMillis(10));
    logger.uploadFailed(uploadKey, "Error during upload3", startTime.plusMillis(15));
    final StatisticsLogger.UploadStatistics statistics = logger.getObjectStatistics(uploadKey);
    assertNotNull(statistics);
    assertTrue(statistics.hasErrors());
    assertEquals(statistics.getErrors().size(), 3);
    assertEquals(statistics.getDuration(), Duration.ofMillis(15));
    assertFalse(statistics.isSuccessful());
  }


  @Test
  public void recordsSuccessEvenWithPreviousErrors() {
    final StatisticsLogger logger = new StatisticsLogger();
    String uploadKey = "upload key";
    final Instant startTime = Instant.now();
    logger.uploadStarted(uploadKey, startTime);
    logger.uploadFailed(uploadKey, "Error during upload1", startTime.plusMillis(5));
    logger.uploadStarted(uploadKey, startTime.plusMillis(5));
    logger.uploadFailed(uploadKey, "Error during upload2", startTime.plusMillis(10));
    logger.uploadStarted(uploadKey, startTime.plusMillis(10));
    logger.uploadFinished(uploadKey, startTime.plusMillis(15));
    final StatisticsLogger.UploadStatistics statistics = logger.getObjectStatistics(uploadKey);
    assertNotNull(statistics);
    assertTrue(statistics.hasErrors());
    assertEquals(statistics.getErrors().size(), 2);
    assertEquals(statistics.getDuration(), Duration.ofMillis(15));
    assertTrue(statistics.isSuccessful());
  }

  @Test
  public void calculatesAverageDuration() {
    final StatisticsLogger logger = new StatisticsLogger();
    final Instant startTime = Instant.now();
    logger.uploadStarted("upload key1", startTime);
    logger.uploadStarted("upload key2", startTime.plus(5, ChronoUnit.MILLIS));
    logger.uploadStarted("upload key3", startTime.plus(10, ChronoUnit.MILLIS));
    logger.uploadStarted("upload key4", startTime.plus(15, ChronoUnit.MILLIS));
    logger.uploadFinished("upload key1", startTime.plus(5, ChronoUnit.MILLIS));
    logger.uploadFinished("upload key2", startTime.plus(10, ChronoUnit.MILLIS));
    logger.uploadFinished("upload key3", startTime.plus(15, ChronoUnit.MILLIS));
    logger.uploadFinished("upload key4", startTime.plus(20, ChronoUnit.MILLIS));

    final StatisticsLogger.SummaryStatistics statistics = logger.getSummaryStatistics();
    assertNotNull(statistics);
    assertEquals(statistics.getAverageDuration(), Duration.ofMillis(5));
  }

  @Test
  public void returnsAllErrors() {
    final StatisticsLogger logger = new StatisticsLogger();
    final Instant startTime = Instant.now();
    logger.uploadStarted("upload key1", startTime);
    logger.uploadStarted("upload key2", startTime.plus(5, ChronoUnit.MILLIS));
    logger.uploadStarted("upload key3", startTime.plus(10, ChronoUnit.MILLIS));
    logger.uploadStarted("upload key4", startTime.plus(15, ChronoUnit.MILLIS));
    logger.uploadFinished("upload key1", startTime.plus(5, ChronoUnit.MILLIS));
    logger.uploadFailed("upload key2", "error1", startTime.plus(10, ChronoUnit.MILLIS));
    logger.uploadFinished("upload key3", startTime.plus(15, ChronoUnit.MILLIS));
    logger.uploadFailed("upload key4", "error2", startTime.plus(20, ChronoUnit.MILLIS));

    final StatisticsLogger.SummaryStatistics statistics = logger.getSummaryStatistics();
    assertNotNull(statistics);
    assertEquals(statistics.getErrors().size(), 2);
  }
}
