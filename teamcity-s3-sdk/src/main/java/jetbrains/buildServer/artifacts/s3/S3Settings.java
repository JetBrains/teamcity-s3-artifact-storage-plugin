package jetbrains.buildServer.artifacts.s3;

import com.amazonaws.services.s3.model.CannedAccessControlList;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.artifacts.s3.S3Constants.PROJECT_ID_PARAM;

public interface S3Settings {
  @NotNull
  String getBucketName();

  int getUrlTtlSeconds();

  @NotNull
  CannedAccessControlList getAcl();

  Map<String, String> toRawSettings();

  @NotNull
  Map<String, String> getProjectSettings();

  void setTtl(long ttl);

  @Nullable
  default String getProjectId() { return getProjectSettings().get(PROJECT_ID_PARAM); }
}
