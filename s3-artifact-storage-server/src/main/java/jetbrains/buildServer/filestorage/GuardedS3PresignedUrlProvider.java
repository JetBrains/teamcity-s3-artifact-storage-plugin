package jetbrains.buildServer.filestorage;

import com.amazonaws.HttpMethod;
import java.io.IOException;
import java.util.Map;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.artifacts.s3.util.ParamUtil;
import jetbrains.buildServer.artifacts.s3.util.S3RegionCorrector;
import jetbrains.buildServer.serverSide.IOGuard;
import jetbrains.buildServer.serverSide.ServerPaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GuardedS3PresignedUrlProvider implements S3PresignedUrlProvider {
  @NotNull
  private final S3PresignedUrlProvider myDelegate;
  @NotNull
  private final ServerPaths myServerPaths;

  public GuardedS3PresignedUrlProvider(@NotNull S3PresignedUrlProvider s3PresignedUrlProvider,
                                       @NotNull ServerPaths serverPaths) {
    myDelegate = s3PresignedUrlProvider;
    myServerPaths = serverPaths;
  }

  @NotNull
  @Override
  public String generateDownloadUrl(@NotNull HttpMethod httpMethod,
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
  public String startMultipartUpload(@NotNull String objectKey, @NotNull S3Settings settings) throws Exception {
    return IOGuard.allowNetworkCall(() -> myDelegate.startMultipartUpload(objectKey, settings));
  }

  @NotNull
  @Override
  public S3Settings settings(@NotNull final Map<String, String> rawSettings, @NotNull Map<String, String> projectSettings) {
    final Map<String, String> sslSettings = ParamUtil.putSslValues(myServerPaths, rawSettings);
    final Map<String, String> correctedSettings = S3RegionCorrector.correctRegion(S3Util.getBucketName(sslSettings), sslSettings);
    return myDelegate.settings(correctedSettings, projectSettings);
  }
}
