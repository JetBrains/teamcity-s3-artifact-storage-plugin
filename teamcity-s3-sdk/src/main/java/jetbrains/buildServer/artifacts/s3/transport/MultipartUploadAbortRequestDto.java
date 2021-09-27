package jetbrains.buildServer.artifacts.s3.transport;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.Used;
import org.jetbrains.annotations.NotNull;

@XmlRootElement(name = "multipartUploadAbortRequest")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class MultipartUploadAbortRequestDto {
  private String objectKey;
  private String uploadId;

  @Used("serialization")
  public MultipartUploadAbortRequestDto() {
  }

  @Used("clients")
  public MultipartUploadAbortRequestDto(@NotNull final String objectKey) {
    this.objectKey = objectKey;
  }

  @Used("clients")
  public MultipartUploadAbortRequestDto(@NotNull final String objectKey, @NotNull final String uploadId) {
    this.objectKey = objectKey;
    this.uploadId = uploadId;
  }

  public String getObjectKey() {
    return objectKey;
  }

  public void setObjectKey(String objectKey) {
    this.objectKey = objectKey;
  }

  public String getUploadId() {
    return uploadId;
  }

  public void setUploadId(String uploadId) {
    this.uploadId = uploadId;
  }
}
