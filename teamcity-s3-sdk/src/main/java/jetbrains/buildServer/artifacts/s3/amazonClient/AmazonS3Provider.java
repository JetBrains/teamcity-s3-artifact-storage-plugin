package jetbrains.buildServer.artifacts.s3.amazonClient;

import java.util.Map;
import jetbrains.buildServer.serverSide.connections.credentials.ConnectionCredentialsException;
import org.jetbrains.annotations.NotNull;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

public interface AmazonS3Provider {

  <T, E extends Exception> T withS3ClientShuttingDownImmediately(@NotNull final Map<String, String> params,
                                                                 @NotNull final String projectId,
                                                                 @NotNull final WithS3Client<T, E> withClient) throws ConnectionCredentialsException;

  <T, E extends Exception> T withS3PresignerShuttingDownImmediately(@NotNull final String bucket,
                                                                    @NotNull final Map<String, String> params,
                                                                    @NotNull final String projectId,
                                                                    @NotNull final WithS3Presigner<T, E> withS3Presigner) throws ConnectionCredentialsException;

  <T, E extends Exception> T withS3Client(@NotNull final Map<String, String> params,
                                          @NotNull final String projectId,
                                          @NotNull final WithS3Client<T, E> withClient) throws ConnectionCredentialsException;

  <T, E extends Exception> T withCorrectingRegionAndAcceleration(@NotNull final Map<String, String> settings,
                                            @NotNull final String projectId,
                                            @NotNull final WithS3Client<T, E> action, boolean shutdownImmediately) throws ConnectionCredentialsException;

  void shutdownClient(@NotNull final S3Client s3Client);

  void shutdownPresigner(@NotNull final S3Presigner s3Presigner);

  <T, E extends Exception> T withCloudFrontClient(@NotNull final Map<String, String> params, @NotNull final String projectId, @NotNull final WithCloudFrontClient<T, E> withClient)
    throws E, ConnectionCredentialsException;

  Map<String, String> correctRegionAndAcceleration(@NotNull final String bucketName,
                                                   @NotNull final Map<String, String> storageSettings,
                                                   @NotNull final String projectId);
}
