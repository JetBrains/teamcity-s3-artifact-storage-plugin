package jetbrains.buildServer.artifacts.s3.publish;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.agent.artifacts.AgentArtifactHelper;
import jetbrains.buildServer.artifacts.ArtifactDataInstance;
import jetbrains.buildServer.artifacts.s3.FileUploadInfo;
import jetbrains.buildServer.artifacts.s3.S3Configuration;
import jetbrains.buildServer.artifacts.s3.exceptions.InvalidSettingsException;
import jetbrains.buildServer.artifacts.s3.publish.logger.S3UploadLogger;
import jetbrains.buildServer.artifacts.s3.publish.presigned.upload.PresignedUrlsProviderClientFactory;
import jetbrains.buildServer.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Test
public class S3ArtifactsPublisherTest extends BaseTestCase {

  @Test
  public void testMultipleArtifactPublishesIncludedInArtifactList() throws IOException {
    HashMap<File, String> artifacts1 = new HashMap<>();
    File file1 = new File("artifact1");
    artifacts1.put(file1, "");
    List<FileUploadInfo> uploadInfos1 = Collections.singletonList(new FileUploadInfo(file1.getName(), file1.getAbsolutePath(), file1.length(), null));

    HashMap<File, String> artifacts2 = new HashMap<>();
    File file2 = new File("artifact2");
    artifacts2.put(file2, "");
    List<FileUploadInfo> uploadInfos2 = Collections.singletonList(new FileUploadInfo(file2.getName(), file2.getAbsolutePath(), file2.length(), null));

    AgentArtifactHelper helper = Mockito.mock(AgentArtifactHelper.class);
    when(helper.isEnabled(any(), any())).thenReturn(true);

    AgentRunningBuild build = Mockito.mock(AgentRunningBuild.class);
    HashMap<String, String> storageSettings = new HashMap<>();
    storageSettings.put("aws.region.name", "test");
    storageSettings.put("secure:aws.secret.access.key", "test");
    storageSettings.put("aws.access.key.id", "test");
    storageSettings.put("aws.credentials.type", "aws.access.keys");
    storageSettings.put("storage.s3.bucket.name", "BUCKET_NAME");
    storageSettings.put("aws.environment", "custom");
    storageSettings.put("aws.service.endpoint", "http://localhost");

    when(build.getSharedConfigParameters()).thenReturn(Collections.emptyMap());
    when(build.getArtifactStorageSettings()).thenReturn(storageSettings);
    when(build.getBuildLogger()).thenReturn(Mockito.mock(BuildProgressLogger.class, RETURNS_DEEP_STUBS));

    CurrentBuildTracker tracker = Mockito.mock(CurrentBuildTracker.class);
    when(tracker.getCurrentBuild()).thenReturn(build);

    BuildAgentConfiguration config = Mockito.mock(BuildAgentConfiguration.class);
    when(config.getAgentHomeDirectory()).thenReturn(new File(""));

    PresignedUrlsProviderClientFactory clientFactory = Mockito.mock(PresignedUrlsProviderClientFactory.class);
    ExtensionHolder holder = Mockito.mock(ExtensionHolder.class);

    EventDispatcher<AgentLifeCycleListener> dispatcher = EventDispatcher.create(AgentLifeCycleListener.class);

    S3FileUploaderFactory uploaderFactory = Mockito.mock(S3FileUploaderFactory.class);
    final S3Configuration s3Configuration = Mockito.mock(S3Configuration.class);
    final S3UploadLogger s3UploadLogger = Mockito.mock(S3UploadLogger.class);
    S3FileUploader uploader = new S3FileUploader(s3Configuration, s3UploadLogger) {
      @Override
      public void upload(@NotNull Map<File, String> filesToUpload,
                         @NotNull Supplier<String> interrupter,
                         Consumer<FileUploadInfo> uploadInfoConsumer) throws InvalidSettingsException {
        uploadInfos1.forEach(i -> uploadInfoConsumer.accept(i));
      }
    };

    when(uploaderFactory.create(any(), any(), any())).thenReturn(uploader);

    S3ArtifactsPublisher publisher = new S3ArtifactsPublisher(helper, dispatcher, tracker, config, clientFactory, uploaderFactory, holder);

    publisher.publishFiles(artifacts1);

    ArgumentCaptor<List<ArtifactDataInstance>> argumentCaptor = ArgumentCaptor.forClass(List.class);

    Mockito.verify(helper, times(1)).publishArtifactList(argumentCaptor.capture(), any());

    List<ArtifactDataInstance> value1 = new ArrayList<>(argumentCaptor.getValue());
    assertEquals("First publishing run should have 1 artifact in the list", value1.size(), 1);

    S3FileUploader uploader2 = new S3FileUploader(s3Configuration, s3UploadLogger) {
      @Override
      public void upload(@NotNull Map<File, String> filesToUpload,
                         @NotNull Supplier<String> interrupter,
                         Consumer<FileUploadInfo> uploadInfoConsumer) throws InvalidSettingsException {
        uploadInfos2.forEach(i -> uploadInfoConsumer.accept(i));
      }
    };
    when(uploaderFactory.create(any(), any(), any())).thenReturn(uploader2);

    publisher.publishFiles(artifacts2);

    Mockito.verify(helper, times(2)).publishArtifactList(argumentCaptor.capture(), any());

    List<ArtifactDataInstance> value2 = argumentCaptor.getValue();
    assertEquals("First publishing run should have 2 artifacts in the list", value2.size(), 2);
  }

  public void testManyFilesAreCorrectlySendToConsumer() throws IOException {
    AgentRunningBuild build = Mockito.mock(AgentRunningBuild.class);
    HashMap<String, String> storageSettings = new HashMap<>();
    storageSettings.put("aws.region.name", "test");
    storageSettings.put("secure:aws.secret.access.key", "test");
    storageSettings.put("aws.access.key.id", "test");
    storageSettings.put("aws.credentials.type", "aws.access.keys");
    storageSettings.put("storage.s3.bucket.name", "BUCKET_NAME");
    storageSettings.put("aws.environment", "custom");
    storageSettings.put("aws.service.endpoint", "http://localhost");

    when(build.getSharedConfigParameters()).thenReturn(Collections.emptyMap());
    when(build.getArtifactStorageSettings()).thenReturn(storageSettings);
    when(build.getBuildLogger()).thenReturn(Mockito.mock(BuildProgressLogger.class, RETURNS_DEEP_STUBS));

    CurrentBuildTracker tracker = Mockito.mock(CurrentBuildTracker.class);
    when(tracker.getCurrentBuild()).thenReturn(build);

    BuildAgentConfiguration config = Mockito.mock(BuildAgentConfiguration.class);
    when(config.getAgentHomeDirectory()).thenReturn(new File(""));

    PresignedUrlsProviderClientFactory clientFactory = Mockito.mock(PresignedUrlsProviderClientFactory.class);
    ExtensionHolder holder = Mockito.mock(ExtensionHolder.class);

    EventDispatcher<AgentLifeCycleListener> dispatcher = EventDispatcher.create(AgentLifeCycleListener.class);

    S3FileUploaderFactory uploaderFactory = Mockito.mock(S3FileUploaderFactory.class);
    final S3Configuration s3Configuration = Mockito.mock(S3Configuration.class);
    final S3UploadLogger s3UploadLogger = Mockito.mock(S3UploadLogger.class);

    HashMap<File, String> artifacts = new HashMap<>();
    List<FileUploadInfo> uploadInfos = new ArrayList<>();
    for (int i = 0; i < 500; i++) {
      final File testFile = Files.createTempFile("test", String.valueOf(i)).toFile();
      artifacts.put(testFile, "");
      uploadInfos.add(new FileUploadInfo(testFile.getName(), testFile.getAbsolutePath(), testFile.length(), null));
    }

    S3FileUploader uploader = new S3FileUploader(s3Configuration, s3UploadLogger) {
      @Override
      public void upload(@NotNull Map<File, String> filesToUpload,
                         @NotNull Supplier<String> interrupter,
                         Consumer<FileUploadInfo> uploadInfoConsumer) throws InvalidSettingsException {
        uploadInfos.stream().parallel().forEach(i -> uploadInfoConsumer.accept(i));
      }
    };

    when(uploaderFactory.create(any(), any(), any())).thenReturn(uploader);

    AgentArtifactHelper helper = new AgentArtifactHelper() {
      @Override
      public void publishArtifactList(@NotNull List<ArtifactDataInstance> artifacts,
                                      @Nullable Map<String, String> commonProperties) {
        assertEmpty(artifacts.stream().filter(Objects::isNull).collect(Collectors.toList()), "Should not contain any nulls");
      }

      @Override
      public boolean isEnabled(@NotNull ArtifactsPublisher publisher, @NotNull String path) {
        return true;
      }
    };

    S3ArtifactsPublisher publisher = new S3ArtifactsPublisher(helper, dispatcher, tracker, config, clientFactory, uploaderFactory, holder);

    publisher.publishFiles(artifacts);
  }

}
