package jetbrains.buildServer.artifacts.s3.lens.integration.dto;

public class UploadInfoEvent {
  private long duration;
  private long numberOfFiles;
  private long totalSize;

  public UploadInfoEvent() {
  }

  public UploadInfoEvent(long duration, long numberOfFiles, long totalSize) {
    this.duration = duration;
    this.numberOfFiles = numberOfFiles;
    this.totalSize = totalSize;
  }

  public long getDuration() {
    return duration;
  }

  public void setDuration(long duration) {
    this.duration = duration;
  }

  public long getNumberOfFiles() {
    return numberOfFiles;
  }

  public void setNumberOfFiles(long numberOfFiles) {
    this.numberOfFiles = numberOfFiles;
  }

  public long getTotalSize() {
    return totalSize;
  }

  public void setTotalSize(long totalSize) {
    this.totalSize = totalSize;
  }
}
