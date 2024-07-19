package jetbrains.buildServer.artifacts.s3.orphans;

import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.Build;
import jetbrains.buildServer.BuildType;
import jetbrains.buildServer.artifacts.ArtifactStorageSettings;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.artifacts.s3.amazonClient.AmazonS3Provider;
import jetbrains.buildServer.configs.DefaultParams;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.AuthUtil;
import jetbrains.buildServer.serverSide.connections.credentials.ConnectionCredentialsException;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.Disposable;
import jetbrains.buildServer.util.NamedThreadFactory;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_COMPATIBLE_STORAGE_TYPE;
import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_STORAGE_TYPE;

/**
 * Service that analyzes contents of S3 storages specified in a project hierarchy and returns a list of folders
 * that do not belong to any existing project{@link SProject}, build type{@link BuildType} or build{@link Build}
 */
public class S3OrphanedArtifactsScanner {
  private final static Logger LOG = Logger.getInstance(S3OrphanedArtifactsScanner.class.getName());

  public static final String FILE_PREFIX = "artifact_scan_";

  public static final String DELIMITER = "/";

  @NotNull
  private final ProjectManager myProjectManager;
  @NotNull
  private final AmazonS3Provider myAmazonS3Provider;
  private final BuildHistory myBuildHistory;
  private final SBuildServer myServer;
  private final ExecutorService myExecutorService;
  private final File myLogsPath;

  private volatile int pathsScanned = 0;
  private final AtomicBoolean isScanning = new AtomicBoolean(false);
  private volatile Instant lastScanTimestamp = Instant.MIN;
  private volatile String lastScanError = null;

  public S3OrphanedArtifactsScanner(@NotNull final SBuildServer server,
                                    @NotNull final ProjectManager projectManager,
                                    @NotNull final AmazonS3Provider amazonS3Provider,
                                    @NotNull final ExecutorServices executorServices,
                                    @NotNull final ServerPaths serverPaths) {
    myProjectManager = projectManager;
    myAmazonS3Provider = amazonS3Provider;
    myServer = server;
    myBuildHistory = myServer.getHistory();
    myExecutorService = executorServices.getLowPriorityExecutorService();
    myLogsPath = serverPaths.getLogsPath();
  }

  public boolean isScanning() {
    return isScanning.get();
  }

  public int getScannedPathsCount() {
    return pathsScanned;
  }

  @NotNull
  public Instant getLastScanTimestamp() {
    return lastScanTimestamp;
  }

  public String getLastScanError() {
    return lastScanError;
  }

  public boolean tryScanArtifacts(@Nullable String projectExternalId, @NotNull SUser user, boolean scanBuilds, boolean calculateSizes, boolean skipErrors) {
    if (isScanning.compareAndSet(false, true)) {
      LOG.debug("Starting scan for orphaned artifacts");
      pathsScanned = 0;
      lastScanError = null;
      try {
        myExecutorService.submit(() -> {
          final Disposable patchedThreadName = NamedThreadFactory.patchThreadName(projectExternalId + "-orphans-scan");
          try {
            final OrphanedArtifacts artifacts = scanArtifacts(projectExternalId, user, scanBuilds, calculateSizes);
            if (artifacts != null) {
              final String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
              final String formattedTimestamp = StringUtils.replaceNonAlphaNumericChars(timestamp, '_');
              final Path filePath = myLogsPath.toPath().resolve(Paths.get(FILE_PREFIX + formattedTimestamp));
              final ObjectMapper objectMapper = new ObjectMapper();

              LOG.debug("Scan finished. Writing results to " + filePath);
              if (skipErrors) {
                Files.write(filePath, objectMapper.writeValueAsBytes(artifacts.getOrphanedPaths()));
              } else {
                Files.write(filePath, objectMapper.writeValueAsBytes(artifacts));
              }
            }
          } catch (Throwable e) {
            LOG.warnAndDebugDetails("Got an error while writing orphaned artifacts to a file", e);
            lastScanError = e.getMessage();
          } finally {
            lastScanTimestamp = Instant.now();
            isScanning.set(false);
            patchedThreadName.dispose();
          }
        });
        return true;
      } catch (RejectedExecutionException e) {
        LOG.warnAndDebugDetails("Cannot scan for orphaned artifacts", e);
        lastScanError = e.getMessage();
        lastScanTimestamp = Instant.now();
        isScanning.set(false);
        return false;
      }
    } else {
      return false;
    }
  }

  @Nullable
  OrphanedArtifacts scanArtifacts(@Nullable String projectExternalId, @NotNull SUser user, boolean scanBuilds, boolean calculateSizes) {
    SProject startingProject;
    if (projectExternalId != null) {
      startingProject = myProjectManager.findProjectByExternalId(projectExternalId);
    } else {
      startingProject = myProjectManager.getRootProject();
    }

    if (startingProject != null) {
      final ArrayList<String> errors = new ArrayList<>();

      final Map<String, SProject> projects = gatherProjects(user, startingProject);

      final Set<OrphanedArtifact> orphanedPaths = new TreeSet<>();
      for (SProject project : projects.values()) {
        LOG.debug("Scanning project '" + project.getExternalId() + "'");
        final Collection<SProjectFeatureDescriptor> storages = project.getOwnFeaturesOfType(DefaultParams.ARTIFACT_STORAGE_TYPE);

        for (SProjectFeatureDescriptor storage : storages) {
          orphanedPaths.addAll(processStorage(scanBuilds, calculateSizes, project, storage, projects, errors));
        }
      }
      return new OrphanedArtifacts(orphanedPaths, errors);
    } else {
      LOG.debug("Could not retrieve starting project for orphaned artifact scan");
      return null;
    }
  }

  private Collection<OrphanedArtifact> processStorage(boolean scanBuilds, boolean calculateSizes, SProject project, SProjectFeatureDescriptor storage, Map<String, SProject> projects, ArrayList<String> errors) {
    Set<OrphanedArtifact> orphanedPaths = new HashSet<>();
    final Map<String, String> parameters = storage.getParameters();
    final String storageType = parameters.get(S3Constants.TEAMCITY_STORAGE_TYPE_KEY);
    if (StringUtil.areEqual(storageType, S3_STORAGE_TYPE) || StringUtil.areEqual(storageType, S3_COMPATIBLE_STORAGE_TYPE)) {
      Set<String> orphans = new HashSet<>();
      String storageName = parameters.get(ArtifactStorageSettings.TEAMCITY_STORAGE_NAME_KEY);
      storageName = storageName != null ? storageName : storage.getId();
      final String bucketName = S3Util.getBucketName(parameters);
      if (bucketName != null) {
        try {
          final boolean bucketExists = IOGuard.allowNetworkCall(() -> myAmazonS3Provider.withS3Client(parameters, project.getProjectId(), s3client -> s3client.doesBucketExistV2(bucketName)));

          if (bucketExists) {
            String basePrefix = S3Util.getPathPrefix(parameters);
            if (basePrefix == null) {
              basePrefix = "";
            }
            orphans.addAll(scanProjects(project, projects, parameters, bucketName, basePrefix));

            final Set<String> existingBuildTypes = project.getBuildTypes().stream().map(SBuildType::getExternalId).collect(Collectors.toSet());
            final String projectPrefix = basePrefix + project.getExternalId() + DELIMITER;

            orphans.addAll(scanBuildTypes(project, projectPrefix, parameters, bucketName, existingBuildTypes));

            if (scanBuilds) {
              orphans.addAll(scanBuilds(project, existingBuildTypes, projectPrefix, parameters, bucketName));
            }

            LOG.debug(String.format("Found %d orphaned paths in storage '%s'", orphans.size(), storageName));
            for (String orphan : orphans) {
              String size = null;
              if (calculateSizes) {
                size = StringUtil.formatFileSize(calculateSize(parameters, bucketName, project.getProjectId(), orphan));
              }
              orphanedPaths.add(new OrphanedArtifact(bucketName, orphan, size));
            }
          } else {
            errors.add("Bucket not found for storage '" + storageName + "' in project " + project.getExternalId());
          }
        } catch (Exception exception) {
          errors.add("Caught error while processing storage: " + storageName + " in project " + project.getExternalId() + ": " + exception.getMessage());
        }
      }
    }

    return orphanedPaths;
  }

  private Set<String> scanBuilds(SProject project, Collection<String> existingBuildTypes, String projectPrefix, Map<String, String> parameters, String bucketName) throws ConnectionCredentialsException {
    Set<String> orphans = new HashSet<>();

    for (String buildType : existingBuildTypes) {
      String buildTypePrefix = projectPrefix + buildType + DELIMITER;
      final ListObjectsV2Result builds = getObjects(parameters, bucketName, project.getProjectId(), buildTypePrefix);
      final List<Long> buildIds = builds.getCommonPrefixes().stream().map(p -> Long.parseLong(stripPath(p, buildTypePrefix))).collect(Collectors.toList());
      final Set<Long> finishedBuilds = myBuildHistory.findEntries(buildIds).stream().map(SFinishedBuild::getBuildId).collect(Collectors.toSet());
      for (String prefix : builds.getCommonPrefixes()) {
        //Only a single thread is doing the writing
        //noinspection NonAtomicOperationOnVolatileField
        pathsScanned++;
        final long buildId = Long.parseLong(stripPath(prefix, buildTypePrefix));
        if (!finishedBuilds.contains(buildId)) {
          final SRunningBuild runningBuild = myServer.findRunningBuildById(buildId);
          if (runningBuild == null) {
            orphans.add(prefix);
          }
        }
      }
    }

    return orphans;
  }

  @NotNull
  private Set<String> scanBuildTypes(SProject project, String projectPrefix, Map<String, String> parameters, String bucketName, Collection<String> existingBuildTypes) throws ConnectionCredentialsException {
    Set<String> orphans = new HashSet<>();

    final ListObjectsV2Result buildTypes = getObjects(parameters, bucketName, project.getProjectId(), projectPrefix);
    for (String prefix : buildTypes.getCommonPrefixes()) {
      //Only a single thread is doing the writing
      //noinspection NonAtomicOperationOnVolatileField
      pathsScanned++;
      final String buildTypeEntry = stripPath(prefix, projectPrefix);
      if (!existingBuildTypes.contains(buildTypeEntry)) {
        orphans.add(prefix);
      }
    }
    return orphans;
  }

  private Set<String> scanProjects(SProject project, Map<String, SProject> projects, Map<String, String> parameters, String bucketName, String basePrefix) throws ConnectionCredentialsException {
    Set<String> orphans = new HashSet<>();

    final ListObjectsV2Result projectsList = getObjects(parameters, bucketName, project.getProjectId(), basePrefix);
    for (String prefix : projectsList.getCommonPrefixes()) {
      //Only a single thread is doing the writing
      //noinspection NonAtomicOperationOnVolatileField
      pathsScanned++;
      final String projectEntry = stripPath(prefix, null);
      if (!projects.containsKey(projectEntry)) {
        orphans.add(basePrefix + prefix);
      }
    }

    return orphans;
  }

  @NotNull
  private static Map<String, SProject> gatherProjects(@NotNull SUser user, SProject startingProject) {
    final Map<String, SProject> projects = new HashMap<>();
    projects.put(startingProject.getExternalId(), startingProject);
    for (SProject subProject : startingProject.getProjects()) {
      if (AuthUtil.hasPermissionToManageProject(user, subProject.getProjectId())) {
        projects.put(subProject.getExternalId(), subProject);
      }
    }
    return projects;
  }


  private long calculateSize(Map<String, String> parameters, String bucketName, String projectId, String prefix) throws ConnectionCredentialsException {
    return IOGuard.allowNetworkCall(() -> myAmazonS3Provider.withS3Client(parameters, projectId, s3client -> {
      final ListObjectsV2Result result = getObjects(parameters, bucketName, projectId, prefix);
      long totalSize = 0;
      for (S3ObjectSummary summary : result.getObjectSummaries()) {
        totalSize += summary.getSize();
      }
      for (String commonPrefix : result.getCommonPrefixes()) {
        totalSize += calculateSize(parameters, bucketName, projectId, commonPrefix);
      }
      return totalSize;
    }));
  }

  @NotNull
  private static String stripPath(String path, @Nullable String prefix) {
    if (path.endsWith(DELIMITER)) {
      path = path.substring(0, path.lastIndexOf(DELIMITER));
    }
    if (prefix != null && path.length() > prefix.length()) {
      path = path.substring(prefix.length());
    }
    return path;
  }

  private ListObjectsV2Result getObjects(Map<String, String> parameters, String bucketName, String projectId, String prefix) throws ConnectionCredentialsException {
    return IOGuard.allowNetworkCall(() -> myAmazonS3Provider.withS3Client(parameters, projectId, s3client -> {
      final ListObjectsV2Request projectFoldersRequest = new ListObjectsV2Request();
      projectFoldersRequest.setBucketName(bucketName);
      projectFoldersRequest.setDelimiter(DELIMITER);
      if (prefix != null) {
        projectFoldersRequest.setPrefix(prefix);
      }
      return s3client.listObjectsV2(projectFoldersRequest);
    }));
  }

}
