package jetbrains.buildServer.artifacts.s3.transport;

import java.util.Collection;
import java.util.Collections;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.Used;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@XmlRootElement(name = "presignedUrl")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class PresignedUrlDto {
  private String objectKey;
  private String uploadId;
  private boolean isMultipart;
  private Collection<PresignedUrlPartDto> presignedUrlParts;

  @Used("serialization")
  public PresignedUrlDto() {
  }

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

  public String getObjectKey() {
    return objectKey;
  }

  public void setObjectKey(@NotNull String objectKey) {
    this.objectKey = objectKey;
  }

  @Nullable
  public String getUploadId() {
    return uploadId;
  }

  public void setUploadId(@Nullable String uploadId) {
    this.uploadId = uploadId;
  }

  public boolean isMultipart() {
    return isMultipart;
  }

  public void setMultipart(boolean multipart) {
    isMultipart = multipart;
  }

  @NotNull
  public Collection<PresignedUrlPartDto> getPresignedUrlParts() {
    return presignedUrlParts;
  }

  public void setPresignedUrlParts(@NotNull Collection<PresignedUrlPartDto> presignedUrlParts) {
    this.presignedUrlParts = presignedUrlParts;
  }
}
