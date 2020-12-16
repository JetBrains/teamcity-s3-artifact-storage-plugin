package jetbrains.buildServer.artifacts.s3.publish.presigned;

import java.io.IOException;
import org.jetbrains.annotations.NotNull;

public interface PresignedUploadProgressListener {
  void onPartUploadFailed(@NotNull S3PresignedUpload upload, @NotNull final Exception e);

  void onPartUploadSuccess(@NotNull S3PresignedUpload upload);

  void onFileUploadFailed(@NotNull S3PresignedUpload upload, @NotNull final Exception e) throws IOException;

  void onFileUploadSuccess(@NotNull S3PresignedUpload upload) throws IOException;
}
