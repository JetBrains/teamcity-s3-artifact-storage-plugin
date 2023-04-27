package jetbrains.buildServer.artifacts.s3.amazonClient;

import com.amazonaws.services.cloudfront.AmazonCloudFront;
import com.amazonaws.services.s3.AmazonS3;
import jetbrains.buildServer.serverSide.connections.credentials.ConnectionCredentialsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface WithCloudFrontClient<T, E extends Exception> {
  @Nullable
  T execute(@NotNull AmazonCloudFront client) throws E, ConnectionCredentialsException;
}
