package jetbrains.buildServer.artifacts.s3.amazonClient;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

public interface WithS3Presigner<T, E extends Exception> {
  @Nullable
  T execute(@NotNull S3Presigner presigner) throws E;
}
