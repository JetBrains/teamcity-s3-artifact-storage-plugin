package jetbrains.buildServer.artifacts.s3.transport;

import com.intellij.openapi.util.Pair;
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
  private String objectKey;
  private int numberOfParts;

  @Used("serialization")
  public PresignedUrlRequestDto() {
  }

  private PresignedUrlRequestDto(@NotNull final String objectKey, final int numberOfParts) {
    this.objectKey = objectKey;
    this.numberOfParts = numberOfParts;
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
  public static PresignedUrlRequestDto from(@Nullable final String objectKey, int numberOfParts) {
    if (objectKey == null) {
      throw new IllegalArgumentException("Object Key cannot be null");
    }
    if (numberOfParts < 1) {
      throw new IllegalArgumentException("Number of parts cannot be < 1");
    }
    return new PresignedUrlRequestDto(objectKey, numberOfParts);
  }

  public String getObjectKey() {
    return objectKey;
  }

  public void setObjectKey(String objectKey) {
    this.objectKey = objectKey;
  }

  public int getNumberOfParts() {
    return numberOfParts;
  }

  public void setNumberOfParts(int numberOfParts) {
    this.numberOfParts = numberOfParts;
  }
}
