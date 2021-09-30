package jetbrains.buildServer.filestorage;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

public interface S3Settings {
  @NotNull
  String getBucketName();

  int getUrlTtlSeconds();

  Map<String, String> toRawSettings();
}
