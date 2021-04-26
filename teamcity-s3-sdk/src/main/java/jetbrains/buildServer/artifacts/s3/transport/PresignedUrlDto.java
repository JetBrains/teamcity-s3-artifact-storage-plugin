package jetbrains.buildServer.artifacts.s3.transport;

import java.util.Collection;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PresignedUrlDto {
  @NotNull
  public final String objectKey;
  @Nullable
  public final String uploadId;
  public final boolean isMultipart;
  @NotNull
  public final Collection<PresignedUrlPartDto> presignedUrlParts;

  private PresignedUrlDto(@NotNull final String objectKey,
                          @Nullable final String uploadId,
                          @NotNull final Collection<PresignedUrlPartDto> presignedUrlParts,
                          final boolean isMultipart) {
    this.uploadId = uploadId;
    this.objectKey = objectKey;
    this.presignedUrlParts = presignedUrlParts;
    this.isMultipart = isMultipart;
  }

  @NotNull
  public static PresignedUrlDto singlePart(@NotNull final String objectKey, @NotNull final String url) {
    return new PresignedUrlDto(objectKey, null, Collections.singletonList(new PresignedUrlPartDto(url, 1)), false);
  }

  @NotNull
  public static PresignedUrlDto multiPart(@NotNull final String objectKey, @NotNull final String uploadId, @NotNull final Collection<PresignedUrlPartDto> presignedUrls) {
    return new PresignedUrlDto(objectKey, uploadId, presignedUrls, true);
  }
}
