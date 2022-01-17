package jetbrains.buildServer.artifacts.s3.transport;

import com.intellij.openapi.util.Pair;
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

  @Used("serialization")
  public PresignedUrlRequestDto() {
  }

  public PresignedUrlRequestDto(@NotNull final String objectKey) {
    this(objectKey, 1);
  }

  private PresignedUrlRequestDto(@NotNull final String objectKey, final int numberOfParts) {
    this.objectKey = objectKey;
    this.numberOfParts = numberOfParts;
    this.digests = null;
  }

  private PresignedUrlRequestDto(@NotNull final String objectKey, @NotNull List<String> digests) {
    this.objectKey = objectKey;
    this.digests = digests;
  }

  @NotNull
  public static PresignedUrlRequestDto from(@NotNull final Map.Entry<String, Integer> mapEntry) {
    return from(mapEntry.getKey(), mapEntry.getValue() != null ? mapEntry.getValue() : 1);
  }

  @NotNull
  public static PresignedUrlRequestDto from(@NotNull final Pair<String, Integer> pair) {
    return from(pair.getFirst(), pair.getSecond() != null ? pair.getSecond() : 1);
  }


  @NotNull
  public static PresignedUrlRequestDto from(@NotNull final String objectKey) {
    return from(objectKey, 1);
  }

  @NotNull
  public static PresignedUrlRequestDto from(@NotNull final String objectKey, int numberOfParts) {
    return from(objectKey, numberOfParts, null);
  }

  @NotNull
  public static PresignedUrlRequestDto from(@NotNull final String objectKey, @NotNull List<String> digests) {
    return from(objectKey, digests.size(), digests);
  }

  @NotNull
  public static PresignedUrlRequestDto from(@NotNull final String objectKey, int numberOfParts, @Nullable List<String> digests) {
    if (numberOfParts < 1) {
      throw new IllegalArgumentException("Number of parts cannot be < 1");
    }
    if (digests == null) {
      return new PresignedUrlRequestDto(objectKey, numberOfParts);
    } else {
      return new PresignedUrlRequestDto(objectKey, digests);
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
}
