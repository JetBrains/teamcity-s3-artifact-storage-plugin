package jetbrains.buildServer.artifacts.s3.publish;

import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.agent.AgentLifeCycleListener;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.agent.CurrentBuildTracker;
import jetbrains.buildServer.agent.artifacts.AgentArtifactHelper;
import jetbrains.buildServer.artifacts.s3.lens.integration.LensIntegrationService;
import jetbrains.buildServer.artifacts.s3.publish.presigned.upload.PresignedUrlsProviderClientFactory;
import jetbrains.buildServer.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_COMPATIBLE_STORAGE_TYPE;

/**
 * Duplicate S3ArtifactsPublisher to use S3 Compatible storage.type
 */
public class S3CompatibleArtifactsPublisher extends S3ArtifactsPublisher {
  public S3CompatibleArtifactsPublisher(@NotNull AgentArtifactHelper helper,
                                        @NotNull EventDispatcher<AgentLifeCycleListener> dispatcher,
                                        @NotNull CurrentBuildTracker tracker,
                                        @NotNull BuildAgentConfiguration buildAgentConfiguration,
                                        @NotNull PresignedUrlsProviderClientFactory presignedUrlsProviderClient,
                                        @NotNull S3FileUploaderFactory uploaderFactory,
                                        @NotNull LensIntegrationService lensIntegrationService,
                                        @NotNull ExtensionHolder extensionHolder) {
    super(helper, dispatcher, tracker, buildAgentConfiguration, presignedUrlsProviderClient, uploaderFactory, lensIntegrationService, extensionHolder);
  }

  @NotNull
  @Override
  public String getType() {
    return S3_COMPATIBLE_STORAGE_TYPE;
  }
}
