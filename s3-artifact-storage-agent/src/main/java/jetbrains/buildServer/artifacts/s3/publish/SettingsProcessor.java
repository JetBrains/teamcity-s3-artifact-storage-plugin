package jetbrains.buildServer.artifacts.s3.publish;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.agent.ssl.TrustedCertificatesDirectory;
import jetbrains.buildServer.artifacts.s3.S3Configuration;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.artifacts.s3.SSLParamUtil;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.artifacts.s3.publish.S3FileUploader.configuration;

public class SettingsProcessor {
  @NotNull
  private final File myHome;

  public SettingsProcessor(@NotNull File home) {
    myHome = home;
  }

  @NotNull
  public S3Configuration processSettings(@NotNull final Map<String, String> sharedConfigParameters, @NotNull final Map<String, String> artifactStorageSettings) {
    final String certDirectory = TrustedCertificatesDirectory.getAllCertificatesDirectoryFromHome(myHome.getPath());
    final Map<String, String> storageSettings = new HashMap<>(SSLParamUtil.putSslDirectory(artifactStorageSettings, certDirectory));
    final S3Configuration s3Configuration = new S3Configuration(configuration(sharedConfigParameters, storageSettings), storageSettings);
    s3Configuration.validate();
    return s3Configuration;
  }
}
