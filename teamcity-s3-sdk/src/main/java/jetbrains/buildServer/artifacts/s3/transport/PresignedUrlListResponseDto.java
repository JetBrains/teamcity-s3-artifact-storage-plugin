package jetbrains.buildServer.artifacts.s3.transport;

import java.util.Collection;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.Used;
import org.jetbrains.annotations.NotNull;

@XmlRootElement(name = "presignedUrlListResponse")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class PresignedUrlListResponseDto {
  private Collection<PresignedUrlDto> presignedUrls;
  private boolean isVersion2;

  @Used("serialization")
  public PresignedUrlListResponseDto() {
  }

  private PresignedUrlListResponseDto(@NotNull final Collection<PresignedUrlDto> presignedUrls, final boolean isVersion2) {
    this.presignedUrls = presignedUrls;
    this.isVersion2 = isVersion2;
  }

  @NotNull
  public static PresignedUrlListResponseDto createV1(@NotNull final List<PresignedUrlDto> presignedUrls) {
    if (presignedUrls.stream().anyMatch(presignedUrlResponseDto -> presignedUrlResponseDto.getUploadId() != null)) {
      throw new IllegalArgumentException("Multipart upload is only supported in version2 of presigned url api");
    }
    return new PresignedUrlListResponseDto(presignedUrls, false);
  }

  @NotNull
  public static PresignedUrlListResponseDto createV2(@NotNull final List<PresignedUrlDto> presignedUrls) {
    return new PresignedUrlListResponseDto(presignedUrls, false);
  }

  public boolean isVersion2() {
    return isVersion2;
  }

  public void setVersion2(boolean version2) {
    isVersion2 = version2;
  }

  @NotNull
  public Collection<PresignedUrlDto> getPresignedUrls() {
    return presignedUrls;
  }

  public void setPresignedUrls(@NotNull Collection<PresignedUrlDto> presignedUrls) {
    this.presignedUrls = presignedUrls;
  }
}
