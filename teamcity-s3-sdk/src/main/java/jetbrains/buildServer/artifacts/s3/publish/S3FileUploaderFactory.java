package jetbrains.buildServer.artifacts.s3.publish;

import java.util.function.Supplier;
import jetbrains.buildServer.artifacts.s3.S3Configuration;
import jetbrains.buildServer.artifacts.s3.publish.logger.S3UploadLogger;
import jetbrains.buildServer.artifacts.s3.publish.presigned.upload.PresignedUrlsProviderClient;
import org.jetbrains.annotations.NotNull;

public interface S3FileUploaderFactory {
  S3FileUploader create(@NotNull S3Configuration s3Configuration,
                        @NotNull S3UploadLogger s3UploadLogger,
                        @NotNull Supplier<PresignedUrlsProviderClient> presignedUrlsProviderClientSupplier);
}
