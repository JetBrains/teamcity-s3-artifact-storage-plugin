package jetbrains.buildServer.artifacts.s3.transport;

import org.jetbrains.annotations.NotNull;

public class PresignedUrlPartDto {
  @NotNull
  public final String url;
  public final int partNumber;

  public PresignedUrlPartDto(@NotNull final String url, final int partNumber) {
    this.url = url;
    this.partNumber = partNumber;
  }
}
