package jetbrains.buildServer.artifacts.s3.transport;

import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

public class PresignedUrlListRequestDto {
  @NotNull
  public final Collection<PresignedUrlRequestDto> presignedUrlRequests;
  public final boolean isVersion2;

  public PresignedUrlListRequestDto(@NotNull final Collection<PresignedUrlRequestDto> requests, final boolean isVersion2) {
    this.presignedUrlRequests = requests;
    this.isVersion2 = isVersion2;
  }

  public static PresignedUrlListRequestDto forObjectKeyMultipart(@NotNull final String objectKey, final int nParts) {
    final PresignedUrlRequestDto request = PresignedUrlRequestDto.from(objectKey, nParts);
    return new PresignedUrlListRequestDto(Collections.singleton(request), true);
  }

  @NotNull
  public static PresignedUrlListRequestDto forObjectKeys(@NotNull final Collection<String> objectKeys) {
    return new PresignedUrlListRequestDto(objectKeys.stream().map(objectKey -> PresignedUrlRequestDto.from(objectKey, 1)).collect(Collectors.toSet()), false);
  }
}
