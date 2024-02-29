package jetbrains.buildServer.artifacts.s3.lens.integration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UploadInfoEvent {
  @JsonProperty("build.artifacts.upload.duration")
  private long duration;
  @JsonProperty("build.artifacts.count")
  private long numberOfFiles;
  @JsonProperty("build.artifacts.size")
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
