package jetbrains.buildServer.artifacts.s3.amazonClient;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import java.util.Map;
import jetbrains.buildServer.serverSide.connections.credentials.ConnectionCredentialsException;
import jetbrains.buildServer.util.amazon.S3Util;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AmazonS3Provider {

  @NotNull
  AmazonS3 fromS3Settings(@NotNull final Map<String, String> s3Settings,
                          @NotNull final String projectId) throws ConnectionCredentialsException;

  @NotNull
  AmazonS3 fromS3Configuration(@NotNull final Map<String, String> s3Settings,
                               @NotNull final String projectId,
                               @Nullable final S3Util.S3AdvancedConfiguration advancedConfiguration)
    throws ConnectionCredentialsException;

  <T, E extends Exception> T withS3ClientShuttingDownImmediately(@NotNull final Map<String, String> params,
                                                                 @NotNull final String projectId,
                                                                 @NotNull final WithS3Client<T, E> withClient) throws ConnectionCredentialsException;

  <T, E extends Exception> T withS3Client(@NotNull final Map<String, String> params,
                                          @NotNull final String projectId,
                                          @NotNull final WithS3Client<T, E> withClient) throws ConnectionCredentialsException;

  <T> T withCorrectingRegionAndAcceleration(@NotNull final Map<String, String> settings,
                                            @NotNull final String projectId,
                                            @NotNull final WithS3Client<T, AmazonS3Exception> action, boolean shutdownImmediately) throws ConnectionCredentialsException;

  void shutdownClient(@NotNull final AmazonS3 s3Client);

  <T, E extends Exception> T withCloudFrontClient(@NotNull final Map<String, String> params, @NotNull final String projectId, @NotNull final WithCloudFrontClient<T, E> withClient)
    throws E, ConnectionCredentialsException;

  Map<String, String> correctRegionAndAcceleration(@NotNull final String bucketName,
                                                   @NotNull final Map<String, String> storageSettings,
                                                   @NotNull final String projectId);
}
