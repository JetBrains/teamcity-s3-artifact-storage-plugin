package jetbrains.buildServer.artifacts.s3.transport;

import com.intellij.openapi.util.Pair;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.Used;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@XmlRootElement(name = "presignedUrlListRequest")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class PresignedUrlListRequestDto {
  private Collection<PresignedUrlRequestDto> presignedUrlRequests;
  private boolean isVersion2;
  @Nullable
  private Long myCustomTtl;

  @Used("xmlSerialization")
  public PresignedUrlListRequestDto() {
  }

  public PresignedUrlListRequestDto(@NotNull final Collection<PresignedUrlRequestDto> requests, final boolean isVersion2) {
    this(requests, isVersion2, null);
  }

  public PresignedUrlListRequestDto(@NotNull final Collection<PresignedUrlRequestDto> requests, final boolean isVersion2, @Nullable Long customTtl) {
    presignedUrlRequests = requests;
    this.isVersion2 = isVersion2;
    myCustomTtl = customTtl;
  }

  @NotNull
  public static PresignedUrlListRequestDto forObjectKeyWithDigest(@NotNull final String objectKey, @Nullable String digest, @Nullable Long ttl) {
    return new PresignedUrlListRequestDto(Collections.singletonList(PresignedUrlRequestDto.from(objectKey, digest)), true, ttl);
  }

  @NotNull
  public static PresignedUrlListRequestDto forObjectKeyMultipart(@NotNull final String objectKey,
                                                                 @Nullable String uploadId,
                                                                 @NotNull final List<String> digests,
                                                                 @Nullable Long ttl) {
    final PresignedUrlRequestDto request = PresignedUrlRequestDto.from(objectKey, uploadId, digests);
    return new PresignedUrlListRequestDto(Collections.singleton(request), true, ttl);
  }

  @NotNull
  public static PresignedUrlListRequestDto forObjectKeys(@NotNull final Collection<String> objectKeys) {
    return new PresignedUrlListRequestDto(objectKeys.stream().map(objectKey -> PresignedUrlRequestDto.from(objectKey)).collect(Collectors.toSet()), false);
  }


  @NotNull
  public static PresignedUrlListRequestDto forObjectKeysWithDigests(@NotNull final Collection<Pair<String, String>> keysWithDigests) {
    return new PresignedUrlListRequestDto(keysWithDigests.stream()
                                                         .map(pair -> PresignedUrlRequestDto.from(pair.getFirst(), pair.getSecond()))
                                                         .collect(Collectors.toSet()),
                                          false, null);
  }

  public Collection<PresignedUrlRequestDto> getPresignedUrlRequests() {
    return presignedUrlRequests;
  }

  @Used("serialization")
  public void setPresignedUrlRequests(Collection<PresignedUrlRequestDto> presignedUrlRequests) {
    this.presignedUrlRequests = presignedUrlRequests;
  }

  public boolean isVersion2() {
    return isVersion2;
  }

  @Used("serialization")
  public void setVersion2(boolean version2) {
    isVersion2 = version2;
  }

  @Nullable
  public Long getCustomTtl() {
    return myCustomTtl;
  }
}
