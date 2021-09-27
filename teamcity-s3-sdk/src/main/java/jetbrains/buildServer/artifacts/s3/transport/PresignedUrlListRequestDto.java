package jetbrains.buildServer.artifacts.s3.transport;

import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.Used;
import org.jetbrains.annotations.NotNull;

@XmlRootElement(name = "presignedUrlListRequest")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class PresignedUrlListRequestDto {
  private Collection<PresignedUrlRequestDto> presignedUrlRequests;
  private boolean isVersion2;

  @Used("xmlSerialization")
  public PresignedUrlListRequestDto() {
  }

  public PresignedUrlListRequestDto(@NotNull final Collection<PresignedUrlRequestDto> requests, final boolean isVersion2) {
    presignedUrlRequests = requests;
    this.isVersion2 = isVersion2;
  }

  @NotNull
  public static PresignedUrlListRequestDto forObjectKeyMultipart(@NotNull final String objectKey, final int nParts) {
    final PresignedUrlRequestDto request = PresignedUrlRequestDto.from(objectKey, nParts);
    return new PresignedUrlListRequestDto(Collections.singleton(request), true);
  }

  @NotNull
  public static PresignedUrlListRequestDto forObjectKeys(@NotNull final Collection<String> objectKeys) {
    return new PresignedUrlListRequestDto(objectKeys.stream().map(objectKey -> PresignedUrlRequestDto.from(objectKey, 1)).collect(Collectors.toSet()), false);
  }

  public Collection<PresignedUrlRequestDto> getPresignedUrlRequests() {
    return presignedUrlRequests;
  }

  public void setPresignedUrlRequests(Collection<PresignedUrlRequestDto> presignedUrlRequests) {
    this.presignedUrlRequests = presignedUrlRequests;
  }

  public boolean isVersion2() {
    return isVersion2;
  }

  public void setVersion2(boolean version2) {
    isVersion2 = version2;
  }
}
