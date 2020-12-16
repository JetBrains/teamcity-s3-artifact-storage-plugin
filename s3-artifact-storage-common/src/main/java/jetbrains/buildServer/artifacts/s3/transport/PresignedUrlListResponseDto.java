package jetbrains.buildServer.artifacts.s3.transport;

import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class PresignedUrlListResponseDto {
  @NotNull
  public final Collection<PresignedUrlDto> presignedUrls;
  public final boolean isVersion2;

  private PresignedUrlListResponseDto(@NotNull final Collection<PresignedUrlDto> presignedUrls, final boolean isVersion2) {
    this.presignedUrls = presignedUrls;
    this.isVersion2 = isVersion2;
  }

  @NotNull
  public static PresignedUrlListResponseDto createV1(@NotNull final List<PresignedUrlDto> presignedUrls) {
    if (presignedUrls.stream().anyMatch(presignedUrlResponseDto -> presignedUrlResponseDto.uploadId != null)) {
      throw new IllegalArgumentException("Multipart upload is only supported in version2 of presigned url api");
    }
    return new PresignedUrlListResponseDto(presignedUrls, false);
  }

  @NotNull
  public static PresignedUrlListResponseDto createV2(@NotNull final List<PresignedUrlDto> presignedUrls) {
    return new PresignedUrlListResponseDto(presignedUrls, false);
  }
}
