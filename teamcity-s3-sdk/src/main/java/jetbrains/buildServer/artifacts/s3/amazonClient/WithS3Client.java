package jetbrains.buildServer.artifacts.s3.amazonClient;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.amazon.awssdk.services.s3.S3Client;

public interface WithS3Client<T, E extends Exception> {
  @Nullable
  T execute(@NotNull S3Client client) throws E;
}
