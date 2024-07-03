package jetbrains.buildServer.artifacts.s3.orphans;

import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AbstractAmazonS3;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.amazonClient.AmazonS3Provider;
import jetbrains.buildServer.artifacts.s3.amazonClient.WithCloudFrontClient;
import jetbrains.buildServer.artifacts.s3.amazonClient.WithS3Client;
import jetbrains.buildServer.configs.DefaultParams;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.amazon.S3Util;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.Mockito;
import org.testng.annotations.Test;
import org.testng.internal.collections.Pair;

import java.util.*;
import java.util.stream.Collectors;

import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_BUCKET_NAME;
import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_STORAGE_TYPE;
import static jetbrains.buildServer.artifacts.s3.orphans.S3OrphanedArtifactsScanner.DELIMITER;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@Test
public class S3OrphanedArtifactsScannerTest extends BaseTestCase {

  public static final String TEST_PROJECT_EXTERNAL_ID = "testProjectExternal";
  public static final String NON_EXISTENT_PROJECT_ID = "NonExistentProject/";
  public static final String BUCKET_ID = "testBucket";
  public static final String EXISTING_BUILD_TYPE_ID = "existingBuildType";

  public void returnsNullWhenStartingProjectDoesNotExist() {
    final SBuildServer server = Mockito.mock(SBuildServer.class);
    final ProjectManager projectManager = Mockito.mock(ProjectManager.class);
    final AmazonS3Provider s3Provider = Mockito.mock(AmazonS3Provider.class);
    final S3OrphanedArtifactsScanner scanner = new S3OrphanedArtifactsScanner(server, projectManager, s3Provider);

    final SUser user = Mockito.mock(SUser.class);
    final OrphanedArtifacts artifacts = scanner.scanArtifacts(TEST_PROJECT_EXTERNAL_ID, user, false, false);
    assertNull(artifacts);
  }

  public void returnsProjectsThatDoNotExist() {
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

    final MockS3 s3 = new MockS3();
    final AmazonS3Provider s3Provider = new MockAmazonProvider(s3);
    final S3OrphanedArtifactsScanner scanner = new S3OrphanedArtifactsScanner(server, projectManager, s3Provider);

    s3.putPrefix("", NON_EXISTENT_PROJECT_ID);
    final SUser user = Mockito.mock(SUser.class);
    final OrphanedArtifacts artifacts = scanner.scanArtifacts(TEST_PROJECT_EXTERNAL_ID, user, false, false);
    assertNotNull(artifacts);
    assertEmpty(artifacts.getErrors());
    final Collection<OrphanedArtifact> orphanedPaths = artifacts.getOrphanedPaths();
    assertNotNull(orphanedPaths);
    assertContains(orphanedPaths, new OrphanedArtifact(BUCKET_ID, NON_EXISTENT_PROJECT_ID, null));
  }

  public void returnsBuildTypesThatDoNotExistInExistingProject() {
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

    final MockS3 s3 = new MockS3();
    final AmazonS3Provider s3Provider = new MockAmazonProvider(s3);
    final S3OrphanedArtifactsScanner scanner = new S3OrphanedArtifactsScanner(server, projectManager, s3Provider);

    final String projectPrefix = TEST_PROJECT_EXTERNAL_ID + DELIMITER;
    s3.putPrefix("", projectPrefix);
    s3.putPrefix(projectPrefix, projectPrefix + "buildType1" + DELIMITER);
    s3.putPrefix(projectPrefix, projectPrefix + "buildType2" + DELIMITER);

    final SUser user = Mockito.mock(SUser.class);
    final OrphanedArtifacts artifacts = scanner.scanArtifacts(TEST_PROJECT_EXTERNAL_ID, user, false, false);
    assertNotNull(artifacts);
    assertEmpty(artifacts.getErrors());
    final Collection<OrphanedArtifact> orphanedPaths = artifacts.getOrphanedPaths();
    assertNotNull(orphanedPaths);
    assertContains(orphanedPaths, new OrphanedArtifact(BUCKET_ID, projectPrefix + "buildType1" + DELIMITER, null));
    assertContains(orphanedPaths, new OrphanedArtifact(BUCKET_ID, projectPrefix + "buildType2" + DELIMITER, null));
  }

  public void returnsBuildsThatDoNotExistInExistingBuildType() {
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

    final MockS3 s3 = new MockS3();
    final AmazonS3Provider s3Provider = new MockAmazonProvider(s3);
    final S3OrphanedArtifactsScanner scanner = new S3OrphanedArtifactsScanner(server, projectManager, s3Provider);

    final String projectPrefix = TEST_PROJECT_EXTERNAL_ID + DELIMITER;
    s3.putPrefix("", projectPrefix);
    final String buildTypePrefix = projectPrefix + buildTypeId + DELIMITER;
    s3.putPrefix(projectPrefix, buildTypePrefix);
    s3.putPrefixes(buildTypePrefix, buildTypePrefix + "1234", buildTypePrefix + "234523", buildTypePrefix + "3456");

    final SUser user = Mockito.mock(SUser.class);
    final OrphanedArtifacts artifacts = scanner.scanArtifacts(TEST_PROJECT_EXTERNAL_ID, user, true, false);
    assertNotNull(artifacts);
    assertEmpty(artifacts.getErrors());
    final Collection<OrphanedArtifact> orphanedPaths = artifacts.getOrphanedPaths();
    assertNotNull(orphanedPaths);
    assertContains(orphanedPaths, new OrphanedArtifact(BUCKET_ID, buildTypePrefix + "1234", null));
    assertContains(orphanedPaths, new OrphanedArtifact(BUCKET_ID, buildTypePrefix + "234523", null));
    assertContains(orphanedPaths, new OrphanedArtifact(BUCKET_ID, buildTypePrefix + "3456", null));
  }

  public void doesNotReturnExistingCompletedBuilds() {
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

    final MockS3 s3 = new MockS3();
    final AmazonS3Provider s3Provider = new MockAmazonProvider(s3);
    final S3OrphanedArtifactsScanner scanner = new S3OrphanedArtifactsScanner(server, projectManager, s3Provider);

    final String projectPrefix = TEST_PROJECT_EXTERNAL_ID + DELIMITER;
    s3.putPrefix("", projectPrefix);
    final String buildTypePrefix = projectPrefix + buildTypeId + DELIMITER;
    s3.putPrefix(projectPrefix, buildTypePrefix);
    s3.putPrefixes(buildTypePrefix, buildTypePrefix + "1234", buildTypePrefix + "234523", buildTypePrefix + "3456");

    final SUser user = Mockito.mock(SUser.class);
    final OrphanedArtifacts artifacts = scanner.scanArtifacts(TEST_PROJECT_EXTERNAL_ID, user, true, false);
    assertNotNull(artifacts);
    assertEmpty(artifacts.getErrors());
    final Collection<OrphanedArtifact> orphanedPaths = artifacts.getOrphanedPaths();
    assertNotNull(orphanedPaths);
    assertContains(orphanedPaths, new OrphanedArtifact(BUCKET_ID, buildTypePrefix + "3456", null));
  }

  public void doesNotReturnExistingRunningBuilds() {
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

    when(server.findRunningBuildById(1234L)).thenReturn(Mockito.mock(SRunningBuild.class));
    when(server.findRunningBuildById(234523L)).thenReturn(Mockito.mock(SRunningBuild.class));

    final MockS3 s3 = new MockS3();
    final AmazonS3Provider s3Provider = new MockAmazonProvider(s3);
    final S3OrphanedArtifactsScanner scanner = new S3OrphanedArtifactsScanner(server, projectManager, s3Provider);

    final String projectPrefix = TEST_PROJECT_EXTERNAL_ID + DELIMITER;
    s3.putPrefix("", projectPrefix);
    final String buildTypePrefix = projectPrefix + buildTypeId + DELIMITER;
    s3.putPrefix(projectPrefix, buildTypePrefix);
    s3.putPrefixes(buildTypePrefix, buildTypePrefix + "1234", buildTypePrefix + "234523", buildTypePrefix + "3456");

    final SUser user = Mockito.mock(SUser.class);
    final OrphanedArtifacts artifacts = scanner.scanArtifacts(TEST_PROJECT_EXTERNAL_ID, user, true, false);
    assertNotNull(artifacts);
    assertEmpty(artifacts.getErrors());
    final Collection<OrphanedArtifact> orphanedPaths = artifacts.getOrphanedPaths();
    assertNotNull(orphanedPaths);
    assertContains(orphanedPaths, new OrphanedArtifact(BUCKET_ID, buildTypePrefix + "3456", null));
  }

  public void calculatesSizesForObjects() {
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

    final MockS3 s3 = new MockS3();
    final AmazonS3Provider s3Provider = new MockAmazonProvider(s3);
    final S3OrphanedArtifactsScanner scanner = new S3OrphanedArtifactsScanner(server, projectManager, s3Provider);

    final String projectPrefix = TEST_PROJECT_EXTERNAL_ID + DELIMITER;
    s3.putPrefix("", projectPrefix);
    final String buildTypePrefix = projectPrefix + buildTypeId + DELIMITER;
    s3.putPrefix(projectPrefix, buildTypePrefix);
    final String build1 = buildTypePrefix + "1234";
    final String build2 = buildTypePrefix + "3456";
    s3.putPrefixes(buildTypePrefix, build1, build2);
    final long size1 = 1024 * 5L;
    final long size2 = 1024 * 45L;
    s3.putObjects(build1, Pair.of("object1", size1), Pair.of("object2", size2) );
    final long size3 = 1024 * 10L;
    final long size4 = 1024 * 45000L;
    s3.putObjects(build2, Pair.of("object3", size3), Pair.of("object4", size4) );

    final SUser user = Mockito.mock(SUser.class);
    final OrphanedArtifacts artifacts = scanner.scanArtifacts(TEST_PROJECT_EXTERNAL_ID, user, true, true);
    assertNotNull(artifacts);
    assertEmpty(artifacts.getErrors());
    final Collection<OrphanedArtifact> orphanedPaths = artifacts.getOrphanedPaths();
    assertNotNull(orphanedPaths);
    assertEquals(2, orphanedPaths.size());
    assertContains(orphanedPaths, new OrphanedArtifact(BUCKET_ID, build1, StringUtil.formatFileSize(size1 + size2)));
    assertContains(orphanedPaths, new OrphanedArtifact(BUCKET_ID, build2, StringUtil.formatFileSize(size3 + size4)));
  }


  static class MockAmazonProvider implements AmazonS3Provider {
    private final AmazonS3 myS3;

    public MockAmazonProvider(AmazonS3 s3) {
      this.myS3 = s3;
    }

    @NotNull
    @Override
    public AmazonS3 fromS3Settings(@NotNull Map<String, String> s3Settings, @NotNull String projectId) {
      return myS3;
    }

    @NotNull
    @Override
    public AmazonS3 fromS3Configuration(@NotNull Map<String, String> s3Settings, @NotNull String projectId, @Nullable S3Util.S3AdvancedConfiguration advancedConfiguration) {
      return myS3;
    }

    @Override
    public <T, E extends Exception> T withS3ClientShuttingDownImmediately(@NotNull Map<String, String> params, @NotNull String projectId, @NotNull WithS3Client<T, E> withClient) {
      try {
        return withClient.execute(myS3);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public <T, E extends Exception> T withS3Client(@NotNull Map<String, String> params, @NotNull String projectId, @NotNull WithS3Client<T, E> withClient) {
      try {
        return withClient.execute(myS3);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public <T> T withCorrectingRegionAndAcceleration(@NotNull Map<String, String> settings, @NotNull String projectId, @NotNull WithS3Client<T, AmazonS3Exception> action, boolean shutdownImmediately) {
      try {
        return action.execute(myS3);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void shutdownClient(@NotNull AmazonS3 s3Client) {

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

  static class MockS3 extends AbstractAmazonS3 {

    private final Map<String, List<Pair<String, Long>>> objects = new HashMap<>();
    private final Map<String, List<String>> prefixes = new HashMap<>();

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

    @Override
    public ListObjectsV2Result listObjectsV2(ListObjectsV2Request request) throws SdkClientException {
      final String prefix = request.getPrefix();

      final ListObjectsV2Result result = new ListObjectsV2Result();
      final List<String> commonPrefixes = prefixes.getOrDefault(prefix, Collections.emptyList());
      result.setCommonPrefixes(commonPrefixes);
      final List<S3ObjectSummary> summaries = objects.getOrDefault(prefix, Collections.emptyList()).stream().map(p -> {
        final S3ObjectSummary summary = new S3ObjectSummary();
        summary.setKey(p.first());
        summary.setSize(p.second());
        return summary;
      }).collect(Collectors.toList());
      result.getObjectSummaries().addAll(summaries);
      return result;
    }

    @Override
    public boolean doesBucketExistV2(String bucketName) throws SdkClientException {
      return true;
    }
  }


}
