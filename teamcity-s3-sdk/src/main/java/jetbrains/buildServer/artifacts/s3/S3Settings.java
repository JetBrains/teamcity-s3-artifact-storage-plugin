package jetbrains.buildServer.artifacts.s3;

import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;

import static jetbrains.buildServer.artifacts.s3.S3Constants.PROJECT_ID_PARAM;

public interface S3Settings {
  @NotNull
  String getBucketName();

  int getUrlTtlSeconds();

  int getUrlExtendedTtlSeconds();

  @NotNull
  ObjectCannedACL getAcl();

  Map<String, String> toRawSettings();

  @NotNull
  Map<String, String> getProjectSettings();

  void setTtl(long ttl);

  @Nullable
  default String getProjectId() { return getProjectSettings().get(PROJECT_ID_PARAM); }
}
