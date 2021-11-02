package jetbrains.buildServer.artifacts.s3.transfer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import jetbrains.buildServer.artifacts.ArtifactStorageSettings;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.transfer.model.Build;
import jetbrains.buildServer.artifacts.s3.transfer.model.Feature;
import jetbrains.buildServer.artifacts.s3.transfer.model.Project;
import jetbrains.buildServer.artifacts.s3.transfer.storage.LocalStorage;
import jetbrains.buildServer.artifacts.s3.transfer.storage.S3Storage;
import jetbrains.buildServer.artifacts.s3.transfer.storage.StorageFactoryImpl;
import junit.framework.TestCase;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

@Test(groups = "artifactTransfer")
public class ProjectProcessorTest extends TestCase {

  @Test
  public void publishesArtifactForSingleBuild() throws IOException, ExecutionException, InterruptedException, TimeoutException {
    Files.createDirectory(Paths.get("target/build1"));
    Files.createDirectory(Paths.get("target/build2"));
    Files.createFile(Paths.get("target/build2/file1.txt"));
    Files.createFile(Paths.get("target/build2/file2.txt"));

    HashMap<String, String> properties = new HashMap<>();
    properties.put(ArtifactStorageSettings.TEAMCITY_STORAGE_NAME_KEY, "teststorage");
    properties.put(ArtifactStorageSettings.TEAMCITY_STORAGE_TYPE_KEY, S3Constants.S3_STORAGE_TYPE);
    Feature feature = new Feature("test", ArtifactStorageSettings.STORAGE_FEATURE_TYPE, properties);
    Project project = new Project("test", "test", Collections.singletonList(feature), Collections.emptyList());

    TeamCityClient client = Mockito.mock(TeamCityClient.class);
    when(client.getDetails(any())).thenReturn(project);

    Build build1 = new Build("build1", "test", "builtType1", "buildType1", "target/build1", Collections.emptyList());
    Build build2 = new Build("build2", "test", "builtType1", "buildType1", "target/build2", Arrays.asList("file1", "file2"));
    when(client.getBuilds("test")).thenReturn(Arrays.asList("build1", "build2"));
    when(client.getArtifacts("build1")).thenReturn(build1);
    when(client.getArtifacts("build2")).thenReturn(build2);

    ProjectProcessor processor = new ProjectProcessor(client, new StorageFactoryImpl(Arrays.asList(new LocalStorage(), new S3Storage())), Executors.newSingleThreadExecutor());
    processor.process("test", ArtifactStorageSettings.DEFAULT_TYPE, "teststorage");
  }
}
