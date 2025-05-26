package jetbrains.buildServer.filestorage;

import com.amazonaws.HttpMethod;
import java.io.IOException;
import java.util.Map;
import jetbrains.buildServer.artifacts.s3.PresignedUrlWithTtl;
import jetbrains.buildServer.artifacts.s3.S3PresignedUrlProvider;
import jetbrains.buildServer.artifacts.s3.S3Settings;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.artifacts.s3.amazonClient.AmazonS3Provider;
import jetbrains.buildServer.artifacts.s3.util.ParamUtil;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.IOGuard;
import jetbrains.buildServer.serverSide.ServerPaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.artifacts.s3.S3Constants.PROJECT_ID_PARAM;

public class GuardedS3PresignedUrlProvider implements S3PresignedUrlProvider {
  @NotNull
  private final S3PresignedUrlProvider myDelegate;
  @NotNull
  private final ServerPaths myServerPaths;
  private final AmazonS3Provider myAmazonS3Provider;

  public GuardedS3PresignedUrlProvider(@NotNull S3PresignedUrlProvider s3PresignedUrlProvider,
                                       @NotNull ServerPaths serverPaths,
                                       @NotNull AmazonS3Provider amazonS3Provider) {
    myDelegate = s3PresignedUrlProvider;
    myServerPaths = serverPaths;
    myAmazonS3Provider = amazonS3Provider;
  }

  @NotNull
  @Override
  public PresignedUrlWithTtl generateDownloadUrl(@NotNull HttpMethod httpMethod,
                                                 @NotNull String objectKey,
                                                 @NotNull S3Settings settings) throws IOException {
    return IOGuard.allowNetworkCall(() -> myDelegate.generateDownloadUrl(httpMethod, objectKey, settings));
  }

  @NotNull
  @Override
  public String generateUploadUrl(@NotNull String objectKey, @Nullable String digest, @NotNull S3Settings settings) throws IOException {
    return IOGuard.allowNetworkCall(() -> myDelegate.generateUploadUrl(objectKey, digest, settings));
  }

  @NotNull
  @Override
  public String generateUploadUrlForPart(@NotNull String objectKey,
                                         @Nullable String digest,
                                         int nPart,
                                         @NotNull String uploadId,
                                         @NotNull S3Settings settings) throws IOException {
    return IOGuard.allowNetworkCall(() -> myDelegate.generateUploadUrlForPart(objectKey, digest, nPart, uploadId, settings));
  }

  @Override
  public void finishMultipartUpload(@NotNull String uploadId,
                                    @NotNull String objectKey,
                                    @NotNull S3Settings settings,
                                    @Nullable String[] etags,
                                    boolean isSuccessful) throws IOException {
    IOGuard.allowNetworkCall(() -> myDelegate.finishMultipartUpload(uploadId, objectKey, settings, etags, isSuccessful));
  }

  @NotNull
  @Override
  public String startMultipartUpload(@NotNull String objectKey, @Nullable String contentType, @NotNull S3Settings settings) throws Exception {
    return IOGuard.allowNetworkCall(() -> myDelegate.startMultipartUpload(objectKey, contentType, settings));
  }

  @NotNull
  @Override
  public S3Settings settings(@NotNull final Map<String, String> rawSettings, @NotNull Map<String, String> projectSettings) {
    final Map<String, String> sslSettings = ParamUtil.putSslValues(myServerPaths, rawSettings);
    String projectId = projectSettings.get(PROJECT_ID_PARAM);
    String bucketName = S3Util.getBucketName(sslSettings);
    if (projectId != null && bucketName != null) {
      final Map<String, String> correctedSettings = myAmazonS3Provider.correctRegionAndAcceleration(
        bucketName,
        sslSettings,
        projectId
      );
      return myDelegate.settings(correctedSettings, projectSettings);
    } else {
      Loggers.CLOUD.debug("Will not correct S3 region, no projectId parameter for bucket with name: " + bucketName);
      return myDelegate.settings(sslSettings, projectSettings);
    }
  }
}
