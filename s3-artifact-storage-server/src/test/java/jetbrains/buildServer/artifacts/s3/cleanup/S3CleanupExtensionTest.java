package jetbrains.buildServer.artifacts.s3.cleanup;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.artifacts.ArtifactData;
import jetbrains.buildServer.artifacts.ArtifactDataInstance;
import jetbrains.buildServer.artifacts.ArtifactListData;
import jetbrains.buildServer.artifacts.ServerArtifactStorageSettingsProvider;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.amazonClient.AmazonS3Provider;
import jetbrains.buildServer.artifacts.s3.amazonClient.WithS3Client;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.artifacts.ServerArtifactHelper;
import jetbrains.buildServer.serverSide.cleanup.BuildCleanupContext;
import jetbrains.buildServer.serverSide.cleanup.BuildCleanupContextEx;
import jetbrains.buildServer.serverSide.impl.FinishedBuildEx;
import jetbrains.buildServer.serverSide.impl.cleanup.CleanupProcessStateEx;
import org.jetbrains.annotations.NotNull;
import org.jmock.Mock;
import org.jmock.core.Invocation;
import org.jmock.core.stub.CustomStub;
import org.jmock.core.stub.DefaultResultStub;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.DeletedObject;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Error;

import static jetbrains.buildServer.artifacts.ArtifactStorageSettings.TEAMCITY_STORAGE_TYPE_KEY;
import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_COMPATIBLE_STORAGE_TYPE;

@Test
public class S3CleanupExtensionTest extends BaseTestCase {
  private RecordingS3Client myS3Client;
  private List<String> myClientBuckets;
  private Map<Long, List<String>> myRemovedFromArtifactList;
  private Set<Long> myBuildsWithErrors;
  private Map<Long, String> myBuildBuckets;

  @Override
  @BeforeMethod(alwaysRun = true)
  public void setUp() throws Exception {
    super.setUp();
    myS3Client = new RecordingS3Client();
    myClientBuckets = new ArrayList<>();
    myRemovedFromArtifactList = new LinkedHashMap<>();
    myBuildsWithErrors = new TreeSet<>();
    myBuildBuckets = new HashMap<>();
    setInternalProperty("cleanup.extensions.s3CleanupExtension.numberOfRetries", "0");
    setInternalProperty(S3Constants.S3_CLEANUP_BATCH_SIZE, "4");
  }

  public void sends_objects_of_several_builds_in_shared_requests() throws Exception {
    cleanup();

    assertEquals(Arrays.asList(Arrays.asList("b1/a", "b1/b", "b2/a", "b2/b"), Arrays.asList("b3/a", "b3/b")), myS3Client.myRequests);
    assertEquals(Collections.singletonList("bucket"), myClientBuckets);
    assertEquals(expectedRemoved(1, 2, 3), myRemovedFromArtifactList);
    assertTrue(myBuildsWithErrors.isEmpty());
  }

  public void reports_object_failed_in_response_only_for_its_build() throws Exception {
    myS3Client.myFailingKeys.add("b2/a");

    cleanup();

    assertEquals(Collections.singleton(2L), myBuildsWithErrors);
    Map<Long, List<String>> expected = expectedRemoved(1, 2, 3);
    expected.put(2L, Collections.singletonList("b"));
    assertEquals(expected, myRemovedFromArtifactList);
  }

  public void keeps_objects_of_failed_and_unsent_requests_in_artifact_list() throws Exception {
    setInternalProperty(S3Constants.S3_CLEANUP_BATCH_SIZE, "2");
    myS3Client.myFailingRequestNumber = 2;

    cleanup();

    assertEquals(2, myS3Client.myRequests.size());
    assertEquals(new TreeSet<>(Arrays.asList(2L, 3L)), myBuildsWithErrors);
    Map<Long, List<String>> expected = expectedRemoved(1, 2, 3);
    expected.put(2L, Collections.emptyList());
    expected.put(3L, Collections.emptyList());
    assertEquals(expected, myRemovedFromArtifactList);
  }

  public void uses_separate_client_and_requests_for_each_bucket() throws Exception {
    myBuildBuckets.put(2L, "other-bucket");

    cleanup();

    assertEquals(Arrays.asList("bucket", "other-bucket"), myClientBuckets);
    assertEquals(Arrays.asList(Arrays.asList("b1/a", "b1/b", "b3/a", "b3/b"), Arrays.asList("b2/a", "b2/b")), myS3Client.myRequests);
    assertEquals(expectedRemoved(1, 3, 2), myRemovedFromArtifactList);
    assertTrue(myBuildsWithErrors.isEmpty());
  }

  private void cleanup() throws Exception {
    S3CleanupExtension extension = createExtension();
    BuildCleanupContext context = createContext(Arrays.asList(1L, 2L, 3L));
    extension.prepareBuildsData(context);
    extension.cleanupBuildsData(context);
  }

  @NotNull
  private static Map<Long, List<String>> expectedRemoved(long... buildIds) {
    Map<Long, List<String>> result = new LinkedHashMap<>();
    for (long buildId : buildIds) {
      result.put(buildId, Arrays.asList("a", "b"));
    }
    return result;
  }

  @NotNull
  private S3CleanupExtension createExtension() throws IOException {
    Mock artifactHelper = mock(ServerArtifactHelper.class);
    artifactHelper.stubs().method("getArtifactList").will(new CustomStub("artifact list of the build") {
      @Override
      public Object invoke(Invocation invocation) {
        long buildId = ((SBuild)invocation.parameterValues.get(0)).getBuildId();
        return artifactList("b" + buildId + "/", ArtifactDataInstance.create("a", 1), ArtifactDataInstance.create("b", 1));
      }
    });
    artifactHelper.stubs().method("removeFromArtifactList").will(new CustomStub("records removed artifacts") {
      @Override
      public Object invoke(Invocation invocation) {
        //noinspection unchecked
        myRemovedFromArtifactList.put(((SBuild)invocation.parameterValues.get(0)).getBuildId(), new ArrayList<>((List<String>)invocation.parameterValues.get(1)));
        return null;
      }
    });

    Mock settingsProvider = mock(ServerArtifactStorageSettingsProvider.class);
    settingsProvider.stubs().method("getStorageSettings").will(new CustomStub("storage settings of the build") {
      @Override
      public Object invoke(Invocation invocation) {
        long buildId = ((SBuild)invocation.parameterValues.get(0)).getBuildId();
        return storageSettings(myBuildBuckets.getOrDefault(buildId, "bucket"));
      }
    });

    Mock project = mock(SProject.class);
    project.stubs().method("getProjectId").will(returnValue("projectId"));
    project.stubs().method("getParameters").will(returnValue(Collections.emptyMap()));
    Mock projectManager = mock(ProjectManager.class);
    projectManager.stubs().method("findProjectById").will(returnValue(project.proxy()));

    Mock amazonS3Provider = mock(AmazonS3Provider.class);
    amazonS3Provider.stubs().method("withCorrectingRegionAndAcceleration").will(new CustomStub("runs the action with the recording client") {
      @Override
      public Object invoke(Invocation invocation) throws Throwable {
        //noinspection unchecked
        myClientBuckets.add(((Map<String, String>)invocation.parameterValues.get(0)).get("storage.s3.bucket.name"));
        return ((WithS3Client<?, ?>)invocation.parameterValues.get(2)).execute(myS3Client);
      }
    });

    return new S3CleanupExtension((ServerArtifactHelper)artifactHelper.proxy(), (ServerArtifactStorageSettingsProvider)settingsProvider.proxy(),
                                  new ServerPaths(createTempDir().getAbsolutePath()), (ProjectManager)projectManager.proxy(),
                                  (AmazonS3Provider)amazonS3Provider.proxy());
  }

  @NotNull
  private BuildCleanupContext createContext(@NotNull List<Long> buildIds) {
    List<FinishedBuildEx> builds = buildIds.stream()
                                           .map(buildId -> {
                                             Mock build = mock(FinishedBuildEx.class);
                                             build.stubs().method("getBuildId").will(returnValue((long)buildId));
                                             build.stubs().method("getProjectId").will(returnValue("projectId"));
                                             return (FinishedBuildEx)build.proxy();
                                           })
                                           .collect(Collectors.toList());

    Map<String, Object> extensionData = new HashMap<>();
    Mock cleanupState = mock(CleanupProcessStateEx.class);
    cleanupState.setDefaultStub(new DefaultResultStub());

    Mock context = mock(BuildCleanupContextEx.class);
    context.stubs().method("getBuilds").will(returnValue(Collections.unmodifiableList(builds)));
    context.stubs().method("getBuildIds").will(returnValue(buildIds));
    context.stubs().method("getCleanupLevel").will(returnValue(CleanupLevel.EVERYTHING));
    context.stubs().method("getCleanupState").will(returnValue(cleanupState.proxy()));
    context.stubs().method("getExtensionData").will(new CustomStub("getExtensionData") {
      @Override
      public Object invoke(Invocation invocation) {
        return extensionData.get((String)invocation.parameterValues.get(0));
      }
    });
    context.stubs().method("setExtensionData").will(new CustomStub("setExtensionData") {
      @Override
      public Object invoke(Invocation invocation) {
        extensionData.put((String)invocation.parameterValues.get(0), invocation.parameterValues.get(1));
        return null;
      }
    });
    context.stubs().method("onBuildCleanupError").will(new CustomStub("records builds with errors") {
      @Override
      public Object invoke(Invocation invocation) {
        myBuildsWithErrors.add(((SBuild)invocation.parameterValues.get(1)).getBuildId());
        return null;
      }
    });
    return (BuildCleanupContext)context.proxy();
  }

  @NotNull
  private static Map<String, String> storageSettings(@NotNull String bucketName) {
    Map<String, String> storageSettings = new HashMap<>();
    storageSettings.put(TEAMCITY_STORAGE_TYPE_KEY, S3_COMPATIBLE_STORAGE_TYPE);
    storageSettings.put("aws.region.name", "us-east-1");
    storageSettings.put("secure:aws.secret.access.key", "secret");
    storageSettings.put("aws.access.key.id", "key");
    storageSettings.put("aws.credentials.type", "aws.access.keys");
    storageSettings.put("storage.s3.bucket.name", bucketName);
    storageSettings.put("aws.environment", "custom");
    storageSettings.put("aws.service.endpoint", "http://localhost:4566");
    return storageSettings;
  }

  @NotNull
  private static ArtifactListData artifactList(@NotNull String pathPrefix, @NotNull ArtifactData... artifacts) {
    Map<String, String> props = Collections.singletonMap(S3Constants.S3_PATH_PREFIX_ATTR, pathPrefix);
    return new ArtifactListData() {
      @NotNull
      @Override
      public List<ArtifactData> getArtifactList() {
        return Arrays.asList(artifacts);
      }

      @NotNull
      @Override
      public Map<String, String> getCommonProperties() {
        return props;
      }

      @NotNull
      @Override
      public String getStorageSettingsId() {
        return "";
      }
    };
  }

  private static class RecordingS3Client implements S3Client {
    final List<List<String>> myRequests = new ArrayList<>();
    final Set<String> myFailingKeys = new HashSet<>();
    int myFailingRequestNumber = -1;

    @Override
    public DeleteObjectsResponse deleteObjects(DeleteObjectsRequest request) {
      List<String> keys = request.delete().objects().stream().map(ObjectIdentifier::key).collect(Collectors.toList());
      myRequests.add(keys);
      if (myRequests.size() == myFailingRequestNumber) {
        throw new IllegalStateException("Request failed");
      }
      List<DeletedObject> deleted = new ArrayList<>();
      List<S3Error> errors = new ArrayList<>();
      for (String key : keys) {
        if (myFailingKeys.contains(key)) {
          errors.add(S3Error.builder().key(key).message("Access Denied").build());
        } else {
          deleted.add(DeletedObject.builder().key(key).build());
        }
      }
      return DeleteObjectsResponse.builder().deleted(deleted).errors(errors).build();
    }

    @Override
    public String serviceName() {
      return SERVICE_NAME;
    }

    @Override
    public void close() {
    }
  }
}
