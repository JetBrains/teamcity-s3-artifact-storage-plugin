package jetbrains.buildServer.artifacts.s3.transport;

import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.Used;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@XmlRootElement(name = "multipartUploadCompleteRequest")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class MultipartUploadCompleteRequestDto {
  private String objectKey;
  private String uploadId;
  private List<String> etags;

  @Used("serialization")
  public MultipartUploadCompleteRequestDto() {
  }

  @Used("clients")
  public MultipartUploadCompleteRequestDto(@NotNull final String objectKey, @NotNull final String uploadId, @NotNull final List<String> etags) {
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

  public List<String> getEtags() {
    return etags;
  }

  public void setEtags(List<String> etags) {
    this.etags = etags;
  }
}
