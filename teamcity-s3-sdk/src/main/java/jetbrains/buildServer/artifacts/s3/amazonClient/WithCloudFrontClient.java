package jetbrains.buildServer.artifacts.s3.amazonClient;

import jetbrains.buildServer.serverSide.connections.credentials.ConnectionCredentialsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;

public interface WithCloudFrontClient<T, E extends Exception> {
  @Nullable
  T execute(@NotNull CloudFrontClient client) throws E, ConnectionCredentialsException;
}
