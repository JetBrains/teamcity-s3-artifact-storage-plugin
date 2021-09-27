package jetbrains.buildServer.artifacts.s3.transport;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.Used;
import org.jetbrains.annotations.NotNull;

@XmlRootElement(name = "multipartUploadStartRequest")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class MultipartUploadStartRequestDto {
  private String objectKey;

  @Used("serialization")
  public MultipartUploadStartRequestDto() {
  }

  @Used("clients")
  public MultipartUploadStartRequestDto(@NotNull final String objectKey) {
    this.objectKey = objectKey;
  }

  public String getObjectKey() {
    return objectKey;
  }

  public void setObjectKey(String objectKey) {
    this.objectKey = objectKey;
  }
}
