package jetbrains.buildServer.filestorage;

import com.amazonaws.services.s3.model.CannedAccessControlList;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public interface S3Settings {
  @NotNull
  String getBucketName();

  int getUrlTtlSeconds();

  @NotNull
  CannedAccessControlList getAcl();

  Map<String, String> toRawSettings();

  @NotNull
  Map<String, String> getProjectSettings();
}
