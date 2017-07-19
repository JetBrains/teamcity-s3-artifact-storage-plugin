package jetbrains.buildServer.artifacts.s3.preSignedUrl;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;

/**
 * Created by Evgeniy Koshkin (evgeniy.koshkin@jetbrains.com) on 19.07.17.
 */
public interface S3PreSignedUrlProvider {
  int getUrlLifetimeSec();

  @NotNull
  String getUploadUrl(@NotNull String bucketName, @NotNull String objectKey, @NotNull Map<String, String> params) throws IOException;

  @NotNull
  String getDownloadUrl(@NotNull String bucketName, @NotNull String objectKey, @NotNull Map<String, String> params) throws IOException;
}
