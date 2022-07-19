package jetbrains.buildServer.artifacts.s3.transport;

import com.intellij.openapi.util.Pair;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.Used;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@XmlRootElement(name = "presignedUrlRequest")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class PresignedUrlRequestDto {
  @Nullable
  private List<String> digests;
  private String objectKey;
  private int numberOfParts;
  @Nullable
  private String httpMethod;

  @Nullable
  private String uploadId;

  @Used("serialization")
  public PresignedUrlRequestDto() {
  }

  private PresignedUrlRequestDto(@NotNull final String objectKey, final int numberOfParts, @Nullable String httpMethod, @Nullable String uploadId) {
    this(objectKey, numberOfParts, null, httpMethod, uploadId);
  }

  private PresignedUrlRequestDto(@NotNull final String objectKey, @NotNull List<String> digests, @Nullable String httpMethod, @Nullable String uploadId) {
    this(objectKey, digests.size(), digests, httpMethod, uploadId);
  }

  public PresignedUrlRequestDto(String objectKey, int numberOfParts, @Nullable List<String> digests, @Nullable String httpMethod, @Nullable String uploadId) {
    this.digests = digests;
    this.objectKey = objectKey;
    this.numberOfParts = numberOfParts;
    this.httpMethod = httpMethod;
    this.uploadId = uploadId;
  }

  @NotNull
  public static PresignedUrlRequestDto from(@NotNull final Map.Entry<String, Integer> mapEntry) {
    return from(mapEntry.getKey(), mapEntry.getValue() != null ? mapEntry.getValue() : 1, null);
  }

  @NotNull
  public static PresignedUrlRequestDto from(@NotNull final Pair<String, Integer> pair) {
    return from(pair.getFirst(), pair.getSecond() != null ? pair.getSecond() : 1, null);
  }


  @NotNull
  public static PresignedUrlRequestDto from(@NotNull final String objectKey) {
    return from(objectKey, 1, null);
  }

  @NotNull
  public static PresignedUrlRequestDto from(@NotNull final String objectKey, int numberOfParts, @Nullable String httpMethod) {
    return from(objectKey, numberOfParts, httpMethod, null, null);
  }

  @NotNull
  public static PresignedUrlRequestDto from(@NotNull final String objectKey, @Nullable String uploadId, @NotNull List<String> digests) {
    return from(objectKey, digests.size(), null, uploadId, digests);
  }

  public static PresignedUrlRequestDto from(String objectKey, String digest) {
    return from(objectKey, 1, null, null, Collections.singletonList(digest));
  }

  @NotNull
  public static PresignedUrlRequestDto from(@NotNull final String objectKey, int numberOfParts, @Nullable String httpMethod,
                                            @Nullable String uploadId,
                                            @Nullable List<String> digests) {
    if (numberOfParts < 1) {
      throw new IllegalArgumentException("Number of parts cannot be < 1");
    }
    if (digests == null) {
      return new PresignedUrlRequestDto(objectKey, numberOfParts, httpMethod, uploadId);
    } else {
      return new PresignedUrlRequestDto(objectKey, digests, httpMethod, uploadId);
    }
  }

  public String getObjectKey() {
    return objectKey;
  }

  public void setObjectKey(@NotNull String objectKey) {
    this.objectKey = objectKey;
  }

  public int getNumberOfParts() {
    return numberOfParts;
  }

  public void setNumberOfParts(int numberOfParts) {
    this.numberOfParts = numberOfParts;
  }

  @Nullable
  public List<String> getDigests() {
    return digests;
  }

  public void setDigests(@Nullable List<String> digests) {
    this.digests = digests;
  }

  @Nullable
  public String getHttpMethod() {
    return httpMethod;
  }

  @Nullable
  public String getUploadId() {
    return uploadId;
  }
}
