package jetbrains.buildServer.artifacts.s3.publish.presigned.upload;

import java.io.File;
import java.util.concurrent.Callable;
import jetbrains.buildServer.artifacts.s3.FileUploadInfo;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.LowLevelS3Client;
import jetbrains.buildServer.util.amazon.S3Util;
import org.jetbrains.annotations.NotNull;

public class S3PresignedUploadFactory {
  @NotNull
  public Callable<FileUploadInfo> create(@NotNull final String artifactPath,
                                         @NotNull final String objectKey,
                                         @NotNull final File file,
                                         @NotNull final S3Util.S3AdvancedConfiguration configuration,
                                         @NotNull final S3SignedUploadManager s3SignedUploadManager,
                                         @NotNull final LowLevelS3Client lowLevelS3Client,
                                         @NotNull final PresignedUploadProgressListener progressListener) {
    final S3PresignedUpload s3PresignedUpload = new S3PresignedUpload(artifactPath, objectKey, file, configuration, s3SignedUploadManager, lowLevelS3Client, progressListener);
    progressListener.setUpload(s3PresignedUpload);
    return s3PresignedUpload;
  }

}
