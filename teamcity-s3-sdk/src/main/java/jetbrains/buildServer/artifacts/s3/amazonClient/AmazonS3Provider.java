package jetbrains.buildServer.artifacts.s3.amazonClient;

import java.util.Map;
import jetbrains.buildServer.serverSide.connections.credentials.ConnectionCredentialsException;
import jetbrains.buildServer.util.amazon.S3Util;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

public interface AmazonS3Provider {

  @NotNull
  S3Client fromS3Settings(@NotNull final Map<String, String> s3Settings,
                          @NotNull final String projectId) throws ConnectionCredentialsException;

  @NotNull
  S3Client fromS3Configuration(@NotNull final Map<String, String> s3Settings,
                               @NotNull final String projectId,
                               @Nullable final S3Util.S3AdvancedConfiguration advancedConfiguration)
    throws ConnectionCredentialsException;

  <T, E extends Exception> T withS3ClientShuttingDownImmediately(@NotNull final Map<String, String> params,
                                                                 @NotNull final String projectId,
                                                                 @NotNull final WithS3Client<T, E> withClient) throws ConnectionCredentialsException;

  <T, E extends Exception> T withS3Client(@NotNull final Map<String, String> params,
                                          @NotNull final String projectId,
                                          @NotNull final WithS3Client<T, E> withClient) throws ConnectionCredentialsException;

  <T, E extends Exception> T withCorrectingRegionAndAcceleration(@NotNull final Map<String, String> settings,
                                            @NotNull final String projectId,
                                            @NotNull final WithS3Client<T, E> action, boolean shutdownImmediately) throws ConnectionCredentialsException;

  void shutdownClient(@NotNull final S3Client s3Client);

  <T, E extends Exception> T withCloudFrontClient(@NotNull final Map<String, String> params, @NotNull final String projectId, @NotNull final WithCloudFrontClient<T, E> withClient)
    throws E, ConnectionCredentialsException;

  Map<String, String> correctRegionAndAcceleration(@NotNull final String bucketName,
                                                   @NotNull final Map<String, String> storageSettings,
                                                   @NotNull final String projectId);
}
