package jetbrains.buildServer.artifacts.s3.lens.integration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UploadFileEvent {
  @JsonProperty("build.artifacts.object.upload.result")
  private String uploadResult;
  @JsonProperty("build.artifacts.object.key")
  private String objectKey;
  @JsonProperty("build.artifacts.object.size")
  private long fileSize;
  @JsonProperty("build.artifacts.object.chunk_count")
  private int numberOfParts;
  @JsonProperty("build.artifacts.object.chunk_size")
  private long chunkSize;
  @JsonProperty("build.artifacts.object.upload.duration")
  private long duration;
  @JsonProperty("build.artifacts.object.upload_retry_count")
  private int restartCount;

  public UploadFileEvent() {
  }

  public UploadFileEvent(String objectKey, long fileSize, int numberOfParts, long chunkSize, long duration, int restartCount, String uploadResult) {
    this.objectKey = objectKey;
    this.fileSize = fileSize;
    this.numberOfParts = numberOfParts > 0 ? numberOfParts : 1;
    this.chunkSize = numberOfParts > 0 ? chunkSize : fileSize;
    this.duration = duration;
    this.restartCount = restartCount;
    this.uploadResult = uploadResult;
  }

  public String getUploadResult() {
    return uploadResult;
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

  public void setUploadResult(String uploadResult) {
    this.uploadResult = uploadResult;
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
           ", uploadResult=" + uploadResult +
           '}';
  }
}
