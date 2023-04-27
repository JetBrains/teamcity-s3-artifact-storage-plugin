package jetbrains.buildServer.artifacts.s3.publish;

import java.util.function.Supplier;
import jetbrains.buildServer.artifacts.s3.S3Configuration;
import jetbrains.buildServer.artifacts.s3.publish.logger.S3UploadLogger;
import jetbrains.buildServer.artifacts.s3.publish.presigned.upload.PresignedUrlsProviderClient;
import jetbrains.buildServer.artifacts.s3.publish.presigned.upload.S3SignedUrlFileUploader;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_STORAGE_TYPE;
import static jetbrains.buildServer.artifacts.s3.S3Constants.TEAMCITY_STORAGE_TYPE_KEY;

public class S3FileUploaderFactoryImpl implements S3FileUploaderFactory {
  @Override
  public S3FileUploader create(@NotNull final S3Configuration s3Configuration,
                               @NotNull final S3UploadLogger s3UploadLogger,
                               @NotNull final Supplier<PresignedUrlsProviderClient> presignedUrlsProviderClientSupplier) {
    if (S3_STORAGE_TYPE.equals(s3Configuration.getSettingsMap().get(TEAMCITY_STORAGE_TYPE_KEY))) {
      return new S3SignedUrlFileUploader(s3Configuration, s3UploadLogger, presignedUrlsProviderClientSupplier);
    }

    return s3Configuration.isUsePresignedUrls()
           ? new S3SignedUrlFileUploader(s3Configuration, s3UploadLogger, presignedUrlsProviderClientSupplier)
           : new S3RegularFileUploader(s3Configuration, s3UploadLogger);
  }
}
