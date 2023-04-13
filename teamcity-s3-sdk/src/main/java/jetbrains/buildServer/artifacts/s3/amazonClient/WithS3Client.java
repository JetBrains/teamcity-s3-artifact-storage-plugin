package jetbrains.buildServer.artifacts.s3.amazonClient;

import com.amazonaws.services.s3.AmazonS3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface WithS3Client<T, E extends Exception> {
  @Nullable
  T execute(@NotNull AmazonS3 client) throws E;
}
