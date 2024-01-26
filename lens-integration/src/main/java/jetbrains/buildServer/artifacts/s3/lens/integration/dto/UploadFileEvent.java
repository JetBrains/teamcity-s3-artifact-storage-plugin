package jetbrains.buildServer.artifacts.s3.lens.integration.dto;

public class UploadFileEvent {
  private boolean successful;

  private String objectKey;

  private long fileSize;
  private int numberOfParts;
  private long chunkSize;
  private long duration;
  private int restartCount;

  public UploadFileEvent() {
  }

  public UploadFileEvent(String objectKey, long fileSize, int numberOfParts, long chunkSize, long duration, int restartCount, boolean isSuccessful) {
    this.objectKey = objectKey;
    this.fileSize = fileSize;
    this.numberOfParts = numberOfParts > 0 ? numberOfParts : 1;
    this.chunkSize = numberOfParts > 0 ? chunkSize : fileSize;
    this.duration = duration;
    this.restartCount = restartCount;
    successful = isSuccessful;
  }

  public boolean isSuccessful() {
    return successful;
  }

  public void setObjectKey(String objectKey) {
    this.objectKey = objectKey;
  }

  public void setFileSize(long fileSize) {
    this.fileSize = fileSize;
  }

  public void setNumberOfParts(int numberOfParts) {
    this.numberOfParts = numberOfParts;
  }

  public void setChunkSize(long chunkSize) {
    this.chunkSize = chunkSize;
  }

  public void setDuration(long duration) {
    this.duration = duration;
  }

  public void setRestartCount(int restartCount) {
    this.restartCount = restartCount;
  }

  public String getObjectKey() {
    return objectKey;
  }

  public long getFileSize() {
    return fileSize;
  }

  public int getNumberOfParts() {
    return numberOfParts;
  }

  public long getChunkSize() {
    return chunkSize;
  }

  public long getDuration() {
    return duration;
  }

  public int getRestartCount() {
    return restartCount;
  }

  public void setSuccessful(boolean successful) {
    this.successful = successful;
  }

  @Override
  public String toString() {
    return "UploadFileEvent{" +
           "objectKey='" + objectKey + '\'' +
           ", fileSize=" + fileSize +
           ", numberOfParts=" + numberOfParts +
           ", chunkSize=" + chunkSize +
           ", duration=" + duration +
           ", restartCount=" + restartCount +
           ", successful=" + successful +
           '}';
  }
}
