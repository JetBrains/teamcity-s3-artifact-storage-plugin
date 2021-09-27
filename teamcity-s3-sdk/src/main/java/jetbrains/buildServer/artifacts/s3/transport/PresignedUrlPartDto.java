package jetbrains.buildServer.artifacts.s3.transport;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.Used;
import org.jetbrains.annotations.NotNull;

@XmlRootElement(name = "presignedUrlPart")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class PresignedUrlPartDto {
  private String url;
  private int partNumber;

  @Used("serialization")
  public PresignedUrlPartDto() {
  }

  public PresignedUrlPartDto(@NotNull final String url, final int partNumber) {
    this.url = url;
    this.partNumber = partNumber;
  }

  @NotNull
  public String getUrl() {
    return url;
  }

  public void setUrl(@NotNull String url) {
    this.url = url;
  }

  public int getPartNumber() {
    return partNumber;
  }

  public void setPartNumber(int partNumber) {
    this.partNumber = partNumber;
  }
}
