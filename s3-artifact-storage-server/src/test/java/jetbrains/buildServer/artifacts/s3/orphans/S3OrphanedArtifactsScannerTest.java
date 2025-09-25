package jetbrains.buildServer.artifacts.s3.orphans;

import java.util.*;
import java.util.stream.Stream;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.amazonClient.AmazonS3Provider;
import jetbrains.buildServer.artifacts.s3.amazonClient.WithCloudFrontClient;
import jetbrains.buildServer.artifacts.s3.amazonClient.WithS3Client;
import jetbrains.buildServer.artifacts.s3.amazonClient.WithS3Presigner;
import jetbrains.buildServer.configs.DefaultParams;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.connections.credentials.ConnectionCredentialsException;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.testng.annotations.Test;
import org.testng.internal.collections.Pair;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_BUCKET_NAME;
import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_STORAGE_TYPE;
import static jetbrains.buildServer.artifacts.s3.orphans.S3OrphanedArtifactsScanner.DELIMITER;
import static jetbrains.buildServer.artifacts.s3.orphans.S3OrphanedArtifactsScanner.FILE_PREFIX;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@Test
public class S3OrphanedArtifactsScannerTest extends BaseTestCase {

  public static final String TEST_PROJECT_EXTERNAL_ID = "testProjectExternal";
  public static final String NON_EXISTENT_PROJECT_ID = "NonExistentProject/";
  public static final String BUCKET_ID = "testBucket";
  public static final String EXISTING_BUILD_TYPE_ID = "existingBuildType";

  public void returnsNullWhenStartingProjectDoesNotExist() throws IOException {
    final SBuildServer server = Mockito.mock(SBuildServer.class);
    final ProjectManager projectManager = Mockito.mock(ProjectManager.class);
    final AmazonS3Provider s3Provider = Mockito.mock(AmazonS3Provider.class);
    final Path rootDir = Files.createTempDirectory("orphanTest");
    ServerPaths myServerPaths = new ServerPaths(rootDir.toFile());
    final S3OrphanedArtifactsScanner scanner = new S3OrphanedArtifactsScanner(server, projectManager, s3Provider, new TestExecutors(), myServerPaths);

    final SUser user = Mockito.mock(SUser.class);
    final OrphanedArtifacts artifacts = scanner.scanArtifacts(TEST_PROJECT_EXTERNAL_ID, user, false, false);
    assertNull(artifacts);
  }

  public void returnsProjectsThatDoNotExist() throws IOException {
    final SBuildServer server = Mockito.mock(SBuildServer.class);
    final BuildHistory buildHistory = Mockito.mock(BuildHistory.class);
    when(server.getHistory()).thenReturn(buildHistory);

    final ProjectManager projectManager = Mockito.mock(ProjectManager.class);
    final SProject testProject = Mockito.mock(SProject.class);
    when(projectManager.findProjectByExternalId(TEST_PROJECT_EXTERNAL_ID)).thenReturn(testProject);
    when(testProject.getProjects()).thenReturn(Collections.emptyList());
    when(testProject.getExternalId()).thenReturn(TEST_PROJECT_EXTERNAL_ID);
    final SProjectFeatureDescriptor storage = Mockito.mock(SProjectFeatureDescriptor.class);
    when(testProject.getOwnFeaturesOfType(DefaultParams.ARTIFACT_STORAGE_TYPE)).thenReturn(Collections.singletonList(storage));
    final HashMap<String, String> parameters = new HashMap<>();
    parameters.put(S3Constants.TEAMCITY_STORAGE_TYPE_KEY, S3_STORAGE_TYPE);
    parameters.put(S3_BUCKET_NAME, BUCKET_ID);

    when(storage.getParameters()).thenReturn(parameters);

    final MockS3 s3 = MockS3.builder()
      .putPrefix("", NON_EXISTENT_PROJECT_ID)
      .build();
    final AmazonS3Provider s3Provider = new MockAmazonProvider(s3.getClient());
    final Path rootDir = Files.createTempDirectory("orphanTest");
    ServerPaths myServerPaths = new ServerPaths(rootDir.toFile());
    final S3OrphanedArtifactsScanner scanner = new S3OrphanedArtifactsScanner(server, projectManager, s3Provider, new TestExecutors(), myServerPaths);

    final SUser user = Mockito.mock(SUser.class);
    final OrphanedArtifacts artifacts = scanner.scanArtifacts(TEST_PROJECT_EXTERNAL_ID, user, false, false);
    assertNotNull(artifacts);
    assertEmpty(artifacts.getErrors());
    final Collection<OrphanedArtifact> orphanedPaths = artifacts.getOrphanedPaths();
    assertNotNull(orphanedPaths);
    assertContains(orphanedPaths, new OrphanedArtifact(BUCKET_ID, NON_EXISTENT_PROJECT_ID, null));
  }

  public void returnsBuildTypesThatDoNotExistInExistingProject() throws IOException {
    final SBuildServer server = Mockito.mock(SBuildServer.class);
    final BuildHistory buildHistory = Mockito.mock(BuildHistory.class);
    when(server.getHistory()).thenReturn(buildHistory);

    final ProjectManager projectManager = Mockito.mock(ProjectManager.class);
    final SProject testProject = Mockito.mock(SProject.class);
    when(projectManager.findProjectByExternalId(TEST_PROJECT_EXTERNAL_ID)).thenReturn(testProject);
    when(testProject.getProjects()).thenReturn(Collections.emptyList());
    when(testProject.getExternalId()).thenReturn(TEST_PROJECT_EXTERNAL_ID);
    final SBuildType buildType = Mockito.mock(SBuildType.class);
    when(buildType.getExternalId()).thenReturn(EXISTING_BUILD_TYPE_ID);
    when(testProject.getBuildTypes()).thenReturn(Collections.singletonList(buildType));
    final SProjectFeatureDescriptor storage = Mockito.mock(SProjectFeatureDescriptor.class);
    when(testProject.getOwnFeaturesOfType(DefaultParams.ARTIFACT_STORAGE_TYPE)).thenReturn(Collections.singletonList(storage));
    final HashMap<String, String> parameters = new HashMap<>();
    parameters.put(S3Constants.TEAMCITY_STORAGE_TYPE_KEY, S3_STORAGE_TYPE);
    parameters.put(S3_BUCKET_NAME, BUCKET_ID);

    when(storage.getParameters()).thenReturn(parameters);

    final String projectPrefix = TEST_PROJECT_EXTERNAL_ID + DELIMITER;
    final MockS3 s3 = MockS3.builder()
                            .putPrefix("", projectPrefix)
                            .putPrefix(projectPrefix, projectPrefix + "buildType1" + DELIMITER)
                            .putPrefix(projectPrefix, projectPrefix + "buildType2" + DELIMITER)
                            .build();
    final AmazonS3Provider s3Provider = new MockAmazonProvider(s3.getClient());
    final Path rootDir = Files.createTempDirectory("orphanTest");
    ServerPaths myServerPaths = new ServerPaths(rootDir.toFile());
    final S3OrphanedArtifactsScanner scanner = new S3OrphanedArtifactsScanner(server, projectManager, s3Provider, new TestExecutors(), myServerPaths);

    final SUser user = Mockito.mock(SUser.class);
    final OrphanedArtifacts artifacts = scanner.scanArtifacts(TEST_PROJECT_EXTERNAL_ID, user, false, false);
    assertNotNull(artifacts);
    assertEmpty(artifacts.getErrors());
    final Collection<OrphanedArtifact> orphanedPaths = artifacts.getOrphanedPaths();
    assertNotNull(orphanedPaths);
    assertContains(orphanedPaths, new OrphanedArtifact(BUCKET_ID, projectPrefix, null));
  }

  public void returnsBuildsThatDoNotExistInExistingBuildType() throws IOException {
    final SBuildServer server = Mockito.mock(SBuildServer.class);
    final BuildHistory buildHistory = Mockito.mock(BuildHistory.class);
    when(server.getHistory()).thenReturn(buildHistory);

    final ProjectManager projectManager = Mockito.mock(ProjectManager.class);
    final SProject testProject = Mockito.mock(SProject.class);
    when(projectManager.findProjectByExternalId(TEST_PROJECT_EXTERNAL_ID)).thenReturn(testProject);
    when(testProject.getProjects()).thenReturn(Collections.emptyList());
    when(testProject.getExternalId()).thenReturn(TEST_PROJECT_EXTERNAL_ID);
    final SBuildType buildType = Mockito.mock(SBuildType.class);
    final String buildTypeId = EXISTING_BUILD_TYPE_ID;
    when(buildType.getExternalId()).thenReturn(buildTypeId);
    when(testProject.getBuildTypes()).thenReturn(Collections.singletonList(buildType));
    final SProjectFeatureDescriptor storage = Mockito.mock(SProjectFeatureDescriptor.class);
    when(testProject.getOwnFeaturesOfType(DefaultParams.ARTIFACT_STORAGE_TYPE)).thenReturn(Collections.singletonList(storage));
    final HashMap<String, String> parameters = new HashMap<>();
    parameters.put(S3Constants.TEAMCITY_STORAGE_TYPE_KEY, S3_STORAGE_TYPE);
    parameters.put(S3_BUCKET_NAME, BUCKET_ID);

    when(storage.getParameters()).thenReturn(parameters);

    when(buildHistory.findEntries(anyList())).thenReturn(Collections.emptyList());

    final String projectPrefix = TEST_PROJECT_EXTERNAL_ID + DELIMITER;
    final String buildTypePrefix = projectPrefix + buildTypeId + DELIMITER;
    final MockS3 s3 = MockS3.builder()
                            .putPrefix("", projectPrefix)
                            .putPrefix(projectPrefix, buildTypePrefix)
                            .putPrefixes(buildTypePrefix, buildTypePrefix + "1234", buildTypePrefix + "234523", buildTypePrefix + "3456")
                            .build();
    final AmazonS3Provider s3Provider = new MockAmazonProvider(s3.getClient());
    final Path rootDir = Files.createTempDirectory("orphanTest");
    ServerPaths myServerPaths = new ServerPaths(rootDir.toFile());
    final S3OrphanedArtifactsScanner scanner = new S3OrphanedArtifactsScanner(server, projectManager, s3Provider, new TestExecutors(), myServerPaths);

    final SUser user = Mockito.mock(SUser.class);
    final OrphanedArtifacts artifacts = scanner.scanArtifacts(TEST_PROJECT_EXTERNAL_ID, user, true, false);
    assertNotNull(artifacts);
    assertEmpty(artifacts.getErrors());
    final Collection<OrphanedArtifact> orphanedPaths = artifacts.getOrphanedPaths();
    assertNotNull(orphanedPaths);
    assertContains(orphanedPaths, new OrphanedArtifact(BUCKET_ID, projectPrefix, null));
  }

  public void doesNotReturnExistingCompletedBuilds() throws IOException {
    final SBuildServer server = Mockito.mock(SBuildServer.class);
    final BuildHistory buildHistory = Mockito.mock(BuildHistory.class);
    long existingBuild = 1234L;
    when(server.getHistory()).thenReturn(buildHistory);
    SFinishedBuild build = Mockito.mock(SFinishedBuild.class);
    when(buildHistory.findEntries(anyCollection())).thenReturn(Collections.singletonList(build));
    when(build.getBuildId()).thenReturn(existingBuild);

    final ProjectManager projectManager = Mockito.mock(ProjectManager.class);
    final SProject testProject = Mockito.mock(SProject.class);
    when(projectManager.findProjectByExternalId(TEST_PROJECT_EXTERNAL_ID)).thenReturn(testProject);
    when(testProject.getProjects()).thenReturn(Collections.emptyList());
    when(testProject.getExternalId()).thenReturn(TEST_PROJECT_EXTERNAL_ID);
    final SBuildType buildType = Mockito.mock(SBuildType.class);
    final String buildTypeId = EXISTING_BUILD_TYPE_ID;
    when(buildType.getExternalId()).thenReturn(buildTypeId);
    when(testProject.getBuildTypes()).thenReturn(Collections.singletonList(buildType));
    final SProjectFeatureDescriptor storage = Mockito.mock(SProjectFeatureDescriptor.class);
    when(testProject.getOwnFeaturesOfType(DefaultParams.ARTIFACT_STORAGE_TYPE)).thenReturn(Collections.singletonList(storage));
    final HashMap<String, String> parameters = new HashMap<>();
    parameters.put(S3Constants.TEAMCITY_STORAGE_TYPE_KEY, S3_STORAGE_TYPE);
    parameters.put(S3_BUCKET_NAME, BUCKET_ID);

    when(storage.getParameters()).thenReturn(parameters);

    final SFinishedBuild build1 = Mockito.mock(SFinishedBuild.class);
    when(build1.getBuildId()).thenReturn(1234L);
    final SFinishedBuild build2 = Mockito.mock(SFinishedBuild.class);
    when(build2.getBuildId()).thenReturn(234523L);
    when(buildHistory.findEntries(anyList())).thenReturn(Arrays.asList(build1, build2));

    final String projectPrefix = TEST_PROJECT_EXTERNAL_ID + DELIMITER;
    final String buildTypePrefix = projectPrefix + buildTypeId + DELIMITER;
    final MockS3 s3 = MockS3.builder()
                            .putPrefix("", projectPrefix)
                            .putPrefix(projectPrefix, buildTypePrefix)
                            .putPrefixes(buildTypePrefix, buildTypePrefix + "1234", buildTypePrefix + "234523", buildTypePrefix + "3456")
                            .build();
    final AmazonS3Provider s3Provider = new MockAmazonProvider(s3.getClient());
    final Path rootDir = Files.createTempDirectory("orphanTest");
    ServerPaths myServerPaths = new ServerPaths(rootDir.toFile());
    final S3OrphanedArtifactsScanner scanner = new S3OrphanedArtifactsScanner(server, projectManager, s3Provider, new TestExecutors(), myServerPaths);

    final SUser user = Mockito.mock(SUser.class);
    final OrphanedArtifacts artifacts = scanner.scanArtifacts(TEST_PROJECT_EXTERNAL_ID, user, true, false);
    assertNotNull(artifacts);
    assertEmpty(artifacts.getErrors());
    final Collection<OrphanedArtifact> orphanedPaths = artifacts.getOrphanedPaths();
    assertNotNull(orphanedPaths);
    assertContains(orphanedPaths, new OrphanedArtifact(BUCKET_ID, buildTypePrefix + "3456", null));
  }

  public void doesNotReturnExistingRunningBuilds() throws IOException {
    final SBuildServer server = Mockito.mock(SBuildServer.class);
    final BuildHistory buildHistory = Mockito.mock(BuildHistory.class);
    when(server.getHistory()).thenReturn(buildHistory);
    when(buildHistory.findEntries(anyList())).thenReturn(Collections.emptyList());

    final ProjectManager projectManager = Mockito.mock(ProjectManager.class);
    final SProject testProject = Mockito.mock(SProject.class);
    when(projectManager.findProjectByExternalId(TEST_PROJECT_EXTERNAL_ID)).thenReturn(testProject);
    when(testProject.getProjects()).thenReturn(Collections.emptyList());
    when(testProject.getExternalId()).thenReturn(TEST_PROJECT_EXTERNAL_ID);
    final SBuildType buildType = Mockito.mock(SBuildType.class);
    final String buildTypeId = EXISTING_BUILD_TYPE_ID;
    when(buildType.getExternalId()).thenReturn(buildTypeId);
    when(testProject.getBuildTypes()).thenReturn(Collections.singletonList(buildType));
    final SProjectFeatureDescriptor storage = Mockito.mock(SProjectFeatureDescriptor.class);
    when(testProject.getOwnFeaturesOfType(DefaultParams.ARTIFACT_STORAGE_TYPE)).thenReturn(Collections.singletonList(storage));
    final HashMap<String, String> parameters = new HashMap<>();
    parameters.put(S3Constants.TEAMCITY_STORAGE_TYPE_KEY, S3_STORAGE_TYPE);
    parameters.put(S3_BUCKET_NAME, BUCKET_ID);

    when(storage.getParameters()).thenReturn(parameters);

    SRunningBuild sRunningBuild1 = Mockito.mock(SRunningBuild.class);
    when(sRunningBuild1.getBuildId()).thenReturn(1234L);
    SRunningBuild sRunningBuild2 = Mockito.mock(SRunningBuild.class);
    when(sRunningBuild2.getBuildId()).thenReturn(234523L);
    List<SRunningBuild> builds = Arrays.asList(sRunningBuild1, sRunningBuild2);

    when(server.getRunningBuilds(any(), any(BuildDataFilter.class))).thenReturn(builds);

    final String projectPrefix = TEST_PROJECT_EXTERNAL_ID + DELIMITER;
    final String buildTypePrefix = projectPrefix + buildTypeId + DELIMITER;
    final MockS3 s3 = MockS3.builder()
                            .putPrefix("", projectPrefix)
                            .putPrefix(projectPrefix, buildTypePrefix)
                            .putPrefixes(buildTypePrefix, buildTypePrefix + "1234", buildTypePrefix + "234523", buildTypePrefix + "3456")
                            .build();
    final AmazonS3Provider s3Provider = new MockAmazonProvider(s3.getClient());
    final Path rootDir = Files.createTempDirectory("orphanTest");
    ServerPaths myServerPaths = new ServerPaths(rootDir.toFile());
    final S3OrphanedArtifactsScanner scanner = new S3OrphanedArtifactsScanner(server, projectManager, s3Provider, new TestExecutors(), myServerPaths);

    final SUser user = Mockito.mock(SUser.class);
    final OrphanedArtifacts artifacts = scanner.scanArtifacts(TEST_PROJECT_EXTERNAL_ID, user, true, false);
    assertNotNull(artifacts);
    assertEmpty(artifacts.getErrors());
    final Collection<OrphanedArtifact> orphanedPaths = artifacts.getOrphanedPaths();
    assertNotNull(orphanedPaths);
    assertContains(orphanedPaths, new OrphanedArtifact(BUCKET_ID, buildTypePrefix + "3456", null));
  }

  public void calculatesSizesForObjects() throws IOException {
    final SBuildServer server = Mockito.mock(SBuildServer.class);
    final BuildHistory buildHistory = Mockito.mock(BuildHistory.class);
    long existingBuild = 1234L;
    when(server.getHistory()).thenReturn(buildHistory);
    SFinishedBuild build = Mockito.mock(SFinishedBuild.class);
    when(buildHistory.findEntries(anyCollection())).thenReturn(Collections.singletonList(build));
    when(build.getBuildId()).thenReturn(existingBuild);

    final ProjectManager projectManager = Mockito.mock(ProjectManager.class);
    final SProject testProject = Mockito.mock(SProject.class);
    when(projectManager.findProjectByExternalId(TEST_PROJECT_EXTERNAL_ID)).thenReturn(testProject);
    when(testProject.getProjects()).thenReturn(Collections.emptyList());
    when(testProject.getExternalId()).thenReturn(TEST_PROJECT_EXTERNAL_ID);
    final SBuildType buildType = Mockito.mock(SBuildType.class);
    final String buildTypeId = EXISTING_BUILD_TYPE_ID;
    when(buildType.getExternalId()).thenReturn(buildTypeId);
    when(testProject.getBuildTypes()).thenReturn(Collections.singletonList(buildType));
    final SProjectFeatureDescriptor storage = Mockito.mock(SProjectFeatureDescriptor.class);
    when(testProject.getOwnFeaturesOfType(DefaultParams.ARTIFACT_STORAGE_TYPE)).thenReturn(Collections.singletonList(storage));
    final HashMap<String, String> parameters = new HashMap<>();
    parameters.put(S3Constants.TEAMCITY_STORAGE_TYPE_KEY, S3_STORAGE_TYPE);
    parameters.put(S3_BUCKET_NAME, BUCKET_ID);

    when(storage.getParameters()).thenReturn(parameters);

    final String projectPrefix = TEST_PROJECT_EXTERNAL_ID + DELIMITER;
    final String buildTypePrefix = projectPrefix + buildTypeId + DELIMITER;
    final String build1 = buildTypePrefix + existingBuild;
    final String build2 = buildTypePrefix + "3456";
    final long size1 = 1024 * 5L;
    final long size2 = 1024 * 45L;
    final long size3 = 1024 * 10L;
    final long size4 = 1024 * 45000L;
    final MockS3 s3 = MockS3.builder()
                            .putPrefix("", projectPrefix)
                            .putPrefix(projectPrefix, buildTypePrefix)
                            .putPrefixes(buildTypePrefix, build1, build2)
                            .putObjects(build1, Pair.of("object1", size1), Pair.of("object2", size2))
                            .putObjects(build2, Pair.of("object3", size3), Pair.of("object4", size4))
                            .build();
    final AmazonS3Provider s3Provider = new MockAmazonProvider(s3.getClient());
    final Path rootDir = Files.createTempDirectory("orphanTest");
    ServerPaths myServerPaths = new ServerPaths(rootDir.toFile());
    final S3OrphanedArtifactsScanner scanner = new S3OrphanedArtifactsScanner(server, projectManager, s3Provider, new TestExecutors(), myServerPaths);

    final SUser user = Mockito.mock(SUser.class);
    final OrphanedArtifacts artifacts = scanner.scanArtifacts(TEST_PROJECT_EXTERNAL_ID, user, true, true);
    assertNotNull(artifacts);
    assertEmpty(artifacts.getErrors());
    final Collection<OrphanedArtifact> orphanedPaths = artifacts.getOrphanedPaths();
    assertNotNull(orphanedPaths);
    assertEquals(1, orphanedPaths.size());
    assertContains(orphanedPaths, new OrphanedArtifact(BUCKET_ID, build2, StringUtil.formatFileSize(size3 + size4)));
  }

  public void schedulesTheScanAndReturnsResults() throws IOException, InterruptedException {
    final SBuildServer server = Mockito.mock(SBuildServer.class);
    final BuildHistory buildHistory = Mockito.mock(BuildHistory.class);
    when(server.getHistory()).thenReturn(buildHistory);

    final ProjectManager projectManager = Mockito.mock(ProjectManager.class);
    final SProject testProject = Mockito.mock(SProject.class);
    when(projectManager.findProjectByExternalId(TEST_PROJECT_EXTERNAL_ID)).thenReturn(testProject);
    when(testProject.getProjects()).thenReturn(Collections.emptyList());
    when(testProject.getExternalId()).thenReturn(TEST_PROJECT_EXTERNAL_ID);
    final SBuildType buildType = Mockito.mock(SBuildType.class);
    final String buildTypeId = EXISTING_BUILD_TYPE_ID;
    when(buildType.getExternalId()).thenReturn(buildTypeId);
    when(testProject.getBuildTypes()).thenReturn(Collections.singletonList(buildType));
    final SProjectFeatureDescriptor storage = Mockito.mock(SProjectFeatureDescriptor.class);
    when(testProject.getOwnFeaturesOfType(DefaultParams.ARTIFACT_STORAGE_TYPE)).thenReturn(Collections.singletonList(storage));
    final HashMap<String, String> parameters = new HashMap<>();
    parameters.put(S3Constants.TEAMCITY_STORAGE_TYPE_KEY, S3_STORAGE_TYPE);
    parameters.put(S3_BUCKET_NAME, BUCKET_ID);

    when(storage.getParameters()).thenReturn(parameters);

    final SFinishedBuild build1 = Mockito.mock(SFinishedBuild.class);
    when(build1.getBuildId()).thenReturn(1234L);
    final SFinishedBuild build2 = Mockito.mock(SFinishedBuild.class);
    when(build2.getBuildId()).thenReturn(234523L);
    when(buildHistory.findEntries(anyList())).thenReturn(Arrays.asList(build1, build2));

    final String projectPrefix = TEST_PROJECT_EXTERNAL_ID + DELIMITER;
    final String buildTypePrefix = projectPrefix + buildTypeId + DELIMITER;
    final MockS3 s3 = MockS3.builder()
                            .putPrefix("", projectPrefix)
                            .putPrefix(projectPrefix, buildTypePrefix)
                            .putPrefixes(buildTypePrefix, buildTypePrefix + "1234", buildTypePrefix + "234523", buildTypePrefix + "3456")
                            .build();
    final AmazonS3Provider s3Provider = new MockAmazonProvider(s3.getClient());
    final Path rootDir = Files.createTempDirectory("orphanTest");
    ServerPaths myServerPaths = new ServerPaths(rootDir.toFile());
    final TestExecutors executors = new TestExecutors();
    final S3OrphanedArtifactsScanner scanner = new S3OrphanedArtifactsScanner(server, projectManager, s3Provider, executors, myServerPaths);

    final SUser user = Mockito.mock(SUser.class);
    scanner.tryScanArtifacts(TEST_PROJECT_EXTERNAL_ID, user, true, false, true);
    executors.getLowPriorityExecutorService().shutdown();
    final boolean terminated = executors.getLowPriorityExecutorService().awaitTermination(60, TimeUnit.SECONDS);
    assertTrue(terminated);
    final File[] files = myServerPaths.getLogsPath().listFiles();
    assertNotNull(files);
    assertTrue(Arrays.stream(files).anyMatch(f -> f.getName().startsWith(FILE_PREFIX)));
  }

  public void schedulesTheScanAndReturnsCorrectStatus() throws IOException, InterruptedException {
    final SBuildServer server = Mockito.mock(SBuildServer.class);
    final BuildHistory buildHistory = Mockito.mock(BuildHistory.class);
    when(server.getHistory()).thenReturn(buildHistory);

    final ProjectManager projectManager = Mockito.mock(ProjectManager.class);
    final SProject testProject = Mockito.mock(SProject.class);
    when(projectManager.findProjectByExternalId(TEST_PROJECT_EXTERNAL_ID)).thenReturn(testProject);
    when(testProject.getProjects()).thenReturn(Collections.emptyList());
    when(testProject.getExternalId()).thenReturn(TEST_PROJECT_EXTERNAL_ID);
    final SBuildType buildType = Mockito.mock(SBuildType.class);
    final String buildTypeId = EXISTING_BUILD_TYPE_ID;
    when(buildType.getExternalId()).thenReturn(buildTypeId);
    when(testProject.getBuildTypes()).thenReturn(Collections.singletonList(buildType));
    final SProjectFeatureDescriptor storage = Mockito.mock(SProjectFeatureDescriptor.class);
    when(testProject.getOwnFeaturesOfType(DefaultParams.ARTIFACT_STORAGE_TYPE)).thenReturn(Collections.singletonList(storage));
    final HashMap<String, String> parameters = new HashMap<>();
    parameters.put(S3Constants.TEAMCITY_STORAGE_TYPE_KEY, S3_STORAGE_TYPE);
    parameters.put(S3_BUCKET_NAME, BUCKET_ID);

    when(storage.getParameters()).thenReturn(parameters);

    final SFinishedBuild build1 = Mockito.mock(SFinishedBuild.class);
    when(build1.getBuildId()).thenReturn(1234L);
    final SFinishedBuild build2 = Mockito.mock(SFinishedBuild.class);
    when(build2.getBuildId()).thenReturn(234523L);
    when(buildHistory.findEntries(anyList())).thenReturn(Arrays.asList(build1, build2));

    final String projectPrefix = TEST_PROJECT_EXTERNAL_ID + DELIMITER;
    final String buildTypePrefix = projectPrefix + buildTypeId + DELIMITER;
    final MockS3 s3 = MockS3.builder()
                            .putPrefix("", projectPrefix)
                            .putPrefix(projectPrefix, buildTypePrefix)
                            .putPrefixes(buildTypePrefix, buildTypePrefix + "1234", buildTypePrefix + "234523", buildTypePrefix + "3456")
                            .build();
    final MockAmazonProvider s3Provider = new MockAmazonProvider(s3.getClient());
    final Path rootDir = Files.createTempDirectory("orphanTest");
    ServerPaths myServerPaths = new ServerPaths(rootDir.toFile());
    final TestExecutors executors = new TestExecutors();
    final S3OrphanedArtifactsScanner scanner = new S3OrphanedArtifactsScanner(server, projectManager, s3Provider, executors, myServerPaths);

    final SUser user = Mockito.mock(SUser.class);
    s3Provider.pause();
    boolean scanStarted = scanner.tryScanArtifacts(TEST_PROJECT_EXTERNAL_ID, user, true, false, true);
    assertTrue(scanStarted);
    assertTrue(scanner.isScanning());

    scanStarted = scanner.tryScanArtifacts(TEST_PROJECT_EXTERNAL_ID, user, true, false, true);
    assertFalse(scanStarted);
    s3Provider.unpause();
    executors.getLowPriorityExecutorService().shutdown();
    final boolean terminated = executors.getLowPriorityExecutorService().awaitTermination(60, TimeUnit.SECONDS);
    assertTrue(terminated);
    assertNull(scanner.getLastScanError());
    assertGreater(scanner.getScannedPathsCount(), 0);
    assertNotNull(scanner.getLastScanTimestamp());
    assertFalse(scanner.isScanning());
    final File[] files = myServerPaths.getLogsPath().listFiles();
    assertNotNull(files);
    assertTrue(Arrays.stream(files).anyMatch(f -> f.getName().startsWith(FILE_PREFIX)));
  }

  static class MockAmazonProvider implements AmazonS3Provider {

    private final Lock lock = new ReentrantLock();

    private final S3Client myS3;

    public MockAmazonProvider(S3Client s3) {
      this.myS3 = s3;
    }

    public void pause() {
      lock.lock();
    }

    public void unpause() {
      lock.unlock();
    }

    @Override
    public <T, E extends Exception> T withS3ClientShuttingDownImmediately(@NotNull Map<String, String> params, @NotNull String projectId, @NotNull WithS3Client<T, E> withClient) {
      lock.lock();
      try {
        return withClient.execute(myS3);
      } catch (Exception e) {
        throw new RuntimeException(e);
      } finally {
        lock.unlock();
      }
    }

    @Override
    public <T, E extends Exception> T withS3PresignerShuttingDownImmediately(@NotNull String bucket,
                                                                             @NotNull Map<String, String> params,
                                                                             @NotNull String projectId,
                                                                             @NotNull WithS3Presigner<T, E> withS3Presigner) throws ConnectionCredentialsException {
      return null;
    }

    @Override
    public <T, E extends Exception> T withS3Client(@NotNull Map<String, String> params, @NotNull String projectId, @NotNull WithS3Client<T, E> withClient) {
      lock.lock();
      try {
        return withClient.execute(myS3);
      } catch (Exception e) {
        throw new RuntimeException(e);
      } finally {
        lock.unlock();
      }
    }

    @Override
    public <T, E extends Exception> T withCorrectingRegionAndAcceleration(@NotNull Map<String, String> settings, @NotNull String projectId, @NotNull WithS3Client<T, E> action, boolean shutdownImmediately) {
      lock.lock();
      try {
        return action.execute(myS3);
      } catch (Exception e) {
        throw new RuntimeException(e);
      } finally {
        lock.unlock();
      }
    }

    @Override
    public void shutdownClient(@NotNull S3Client s3Client) {

    }

    @Override
    public void shutdownPresigner(@NotNull S3Presigner s3Presigner) {

    }

    @Override
    public <T, E extends Exception> T withCloudFrontClient(@NotNull Map<String, String> params, @NotNull String projectId, @NotNull WithCloudFrontClient<T, E> withClient) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, String> correctRegionAndAcceleration(@NotNull String bucketName, @NotNull Map<String, String> storageSettings, @NotNull String projectId) {
      return storageSettings;
    }
  }

  static class MockS3 {

    private final S3Client s3;
    private final Map<String, List<Pair<String, Long>>> objects;
    private final Map<String, List<String>> prefixes;

    private MockS3(S3Client s3, Map<String, List<Pair<String, Long>>> objects, Map<String, List<String>> prefixes) {
      this.s3 = s3;
      this.objects = objects;
      this.prefixes = prefixes;
    }

    @SafeVarargs
    public final void putObjects(String prefix, Pair<String, Long>... objects) {
      this.objects.put(prefix, Arrays.asList(objects));
    }

    public void putPrefixes(String prefix, String... objects) {
      this.prefixes.put(prefix, Arrays.asList(objects));
    }

    public void putPrefix(String prefix, String object) {
      prefixes.computeIfAbsent(prefix, k -> new ArrayList<>()).add(object);
    }

    public HeadBucketResponse headBucket(HeadBucketRequest headBucketRequest) {
      return s3.headBucket(headBucketRequest);
    }

    public ListObjectsV2Iterable listObjectsV2Paginator(ListObjectsV2Request listObjectsV2Request) {
      return s3.listObjectsV2Paginator(listObjectsV2Request);
    }

    public S3Client getClient() {
      return s3;
    }

    public static Builder builder() {
      return new Builder();
    }

    public static class Builder {
      private final S3Client s3 = Mockito.mock(S3Client.class);
      private final Map<String, List<Pair<String, Long>>> objects = new HashMap<>();
      private final Map<String, List<String>> prefixes = new HashMap<>();

      @SafeVarargs
      public final Builder putObjects(String prefix, Pair<String, Long>... objects) {
        this.objects.put(prefix, Arrays.asList(objects));
        return this;
      }

      public Builder putPrefixes(String prefix, String... objects) {
        this.prefixes.put(prefix, Arrays.asList(objects));
        return this;
      }

      public Builder putPrefix(String prefix, String object) {
        prefixes.computeIfAbsent(prefix, k -> new ArrayList<>()).add(object);
        return this;
      }

      public MockS3 build() {
        when(s3.headBucket(any(HeadBucketRequest.class)))
          .thenReturn(HeadBucketResponse.builder().build());

        when(s3.listObjectsV2Paginator(any(ListObjectsV2Request.class)))
          .thenAnswer((Answer<ListObjectsV2Iterable>) invocation -> {
            ListObjectsV2Request request = invocation.getArgument(0);
            final String prefix = request.prefix() == null ? "" : request.prefix();

            final ListObjectsV2Response.Builder resultBuilder = ListObjectsV2Response.builder();
            final List<String> commonPrefixes = prefixes.getOrDefault(prefix, Collections.emptyList());
            resultBuilder.commonPrefixes(commonPrefixes.stream().map(p -> CommonPrefix.builder().prefix(p).build()).collect(Collectors.toList()));
            final List<S3Object> summaries = objects.getOrDefault(prefix, Collections.emptyList()).stream().map(p -> {
              final S3Object.Builder summaryBuilder = S3Object.builder();
              summaryBuilder.key(p.first());
              summaryBuilder.size(p.second());
              return summaryBuilder.build();
            }).collect(Collectors.toList());
            resultBuilder.contents(summaries);
            final ListObjectsV2Iterable responseIterator = Mockito.mock(ListObjectsV2Iterable.class);
            when(responseIterator.stream()).thenReturn(Stream.of(resultBuilder.build()));
            return responseIterator;
          });
        return new MockS3(s3, objects, prefixes);
      }
    }
  }

  static class TestExecutors implements ExecutorServices {

    private final ScheduledExecutorService normalExecutor = Executors.newSingleThreadScheduledExecutor();

    private final ExecutorService lowPrioExecutor = Executors.newSingleThreadExecutor();


    @NotNull
    @Override
    public ScheduledExecutorService getNormalExecutorService() {
      return normalExecutor;
    }

    @NotNull
    @Override
    public ExecutorService getLowPriorityExecutorService() {
      return lowPrioExecutor;
    }
  }


}
