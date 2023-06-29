package jetbrains.buildServer.artifacts.s3.publish;

import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.agent.ServerProvidedProperties;
import jetbrains.buildServer.agent.ssl.TrustedCertificatesDirectory;
import jetbrains.buildServer.artifacts.s3.S3Configuration;
import jetbrains.buildServer.artifacts.s3.SSLParamUtil;
import jetbrains.buildServer.util.amazon.S3Util;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.artifacts.s3.publish.S3FileUploader.configuration;

public class SettingsProcessor {

  @NotNull private final BuildAgentConfiguration myBuildAgentConfiguration;

  public SettingsProcessor(@NotNull BuildAgentConfiguration buildAgentConfiguration) {
    myBuildAgentConfiguration = buildAgentConfiguration;
  }

  @NotNull
  public S3Configuration processSettings(@NotNull final Map<String, String> sharedConfigParameters, @NotNull final Map<String, String> artifactStorageSettings) {
    final String certDirectory = TrustedCertificatesDirectory.getAllCertificatesDirectory(myBuildAgentConfiguration);
    final Map<String, String> storageSettings = new HashMap<>(SSLParamUtil.putSslDirectory(artifactStorageSettings, certDirectory));
    final S3Util.S3AdvancedConfiguration s3AdvancedConfiguration = configuration(sharedConfigParameters, storageSettings);

    String projectId = sharedConfigParameters.get(ServerProvidedProperties.TEAMCITY_PROJECT_ID_PARAM);
    int nThreadsForFileParts = jetbrains.buildServer.artifacts.s3.S3Util.getNumberOfThreadsForFileParts(sharedConfigParameters);

    final S3Configuration s3Configuration = new S3Configuration(s3AdvancedConfiguration, storageSettings, projectId, nThreadsForFileParts);
    s3Configuration.validate();
    return s3Configuration;
  }
}
