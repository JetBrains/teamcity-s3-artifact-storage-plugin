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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;

import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_COMPATIBLE_STORAGE_TYPE;
import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_PATH_PREFIX_SETTING;
import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_STORAGE_TYPE;

/**
 * Service that analyzes contents of S3 storages specified in a project hierarchy and returns a list of folders
 * that do not belong to any existing project{@link SProject}, build type{@link BuildType} or build{@link Build}
 */
public class S3OrphanedArtifactsScanner {
  private final static Logger LOG = Logger.getInstance(S3OrphanedArtifactsScanner.class.getName());

  public static final String FILE_PREFIX = "artifact_scan_";

  public static final String DELIMITER = "/";

  private static final Pattern NUMERIC_BUILD_ID = Pattern.compile("^[0-9]+$");

  @NotNull
  private final ProjectManager myProjectManager;
  @NotNull
  private final AmazonS3Provider myAmazonS3Provider;
  private final BuildHistory myBuildHistory;
  private final SBuildServer myServer;
  private final ExecutorService myExecutorService;
  private final File myLogsPath;

  private final LongAdder scannedPaths = new LongAdder();
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
    return scannedPaths.intValue();
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
      scannedPaths.reset();
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

      final List<SProject> projects = gatherProjects(user, startingProject);

      final Set<OrphanedArtifact> orphanedPaths = new TreeSet<>();
      for (SProject project : projects) {
        LOG.debug("Scanning project '" + project.getExternalId() + "'");
        final Collection<SProjectFeatureDescriptor> storages = project.getOwnFeaturesOfType(DefaultParams.ARTIFACT_STORAGE_TYPE);

        for (SProjectFeatureDescriptor storage : storages) {
          orphanedPaths.addAll(processStorage(scanBuilds, calculateSizes, project, storage, errors));
        }
      }
      return new OrphanedArtifacts(orphanedPaths, errors);
    } else {
      LOG.debug("Could not retrieve starting project for orphaned artifact scan");
      return null;
    }
  }

  private Collection<OrphanedArtifact> processStorage(boolean scanBuilds, boolean calculateSizes, SProject project, SProjectFeatureDescriptor storage, ArrayList<String> errors) {
    Set<OrphanedArtifact> orphanedPaths = new HashSet<>();
    final Map<String, String> parameters = storage.getParameters();
    final String storageType = parameters.get(S3Constants.TEAMCITY_STORAGE_TYPE_KEY);
    if (StringUtil.areEqual(storageType, S3_STORAGE_TYPE) || StringUtil.areEqual(storageType, S3_COMPATIBLE_STORAGE_TYPE)) {
      Set<String> orphans = new HashSet<>();
      String storageName = parameters.get(ArtifactStorageSettings.TEAMCITY_STORAGE_NAME_KEY);
      storageName = storageName != null ? storageName : storage.getId();
      final String bucketName = S3Util.getBucketName(parameters);

      if (bucketName == null) {
        return orphanedPaths;
      }

      try {
        String basePrefix = parameters.get(S3_PATH_PREFIX_SETTING);

        if (basePrefix != null && !basePrefix.endsWith("/")) {
          basePrefix += "/";
        }

        Set<ProjectEntry> outdatedEntries = scanBasePath(project.getProjectId(), basePrefix, bucketName, parameters);

        for (ProjectEntry projectEntry : outdatedEntries) {
          if (projectEntry.isOutdated()) {
            String path = projectEntry.getPath();
            LOG.debug("Found an outdated project at " + path);
            orphans.add(path);
            continue;
          }

          for (BuildTypeEntry buildTypeEntry : projectEntry.getBuildTypeEntries()) {
            if (buildTypeEntry.isOutdated()) {
              String path = buildTypeEntry.getPath();
              LOG.debug("Found an outdated build type at " + path);
              orphans.add(path);
              continue;
            }

            if (!scanBuilds) {
              continue;
            }

            for (BuildEntry buildEntry : buildTypeEntry.getBuildEntries()) {
              orphans.add(buildEntry.getPath());
            }
          }
        }

        LOG.debug(String.format("Found %d orphaned paths in storage '%s'", orphans.size(), storageName));
        for (String orphan : orphans) {
          String size = null;
          if (calculateSizes) {
            size = StringUtil.formatFileSize(calculateSize(parameters, bucketName, project.getProjectId(), orphan));
          }
          orphanedPaths.add(new OrphanedArtifact(bucketName, orphan, size));
        }
      } catch (Exception exception) {
        errors.add("Caught error while processing storage: " + storageName + " in project " + project.getExternalId() + ": " + exception.getMessage());
      }
    }

    return orphanedPaths;
  }

  private Set<ProjectEntry> scanBasePath(String projectId, String basePrefix, String bucketName, Map<String, String> parameters) throws ConnectionCredentialsException {
    ListObjectsV2Result projectsList = getObjects(parameters, bucketName, projectId, basePrefix);
    List<String> paths = projectsList.getCommonPrefixes();

    Set<ProjectEntry> projectEntries = new HashSet<>(paths.size());
    for (String path : paths) {
      projectEntries.add(scanProjectPath(projectId, path, bucketName, parameters));
    }

    scannedPaths.add(paths.size());

    return projectEntries;
  }

  private ProjectEntry scanProjectPath(String projectId, String projectPath, String bucketName, Map<String, String> parameters) throws ConnectionCredentialsException {
    ListObjectsV2Result projectPaths = getObjects(parameters, bucketName, projectId, projectPath);
    List<String> paths = projectPaths.getCommonPrefixes();

    if (paths.isEmpty()) {
      return new ProjectEntry(projectPath, Collections.emptySet());
    }

    Set<BuildTypeEntry> buildTypeEntries = new HashSet<>(paths.size());
    for (String path : paths) {
      buildTypeEntries.add(scanBuildTypePath(projectId, path, bucketName, parameters));
    }

    scannedPaths.add(paths.size());

    return new ProjectEntry(projectPath, buildTypeEntries);
  }

  private BuildTypeEntry scanBuildTypePath(String projectId, String buildTypePath, String bucketName, Map<String, String> parameters) throws ConnectionCredentialsException {
    ListObjectsV2Result builds = getObjects(parameters, bucketName, projectId, buildTypePath);
    List<String> paths = builds.getCommonPrefixes();

    if (paths.isEmpty()) {
      LOG.debug("Found path that doesn't correlate to the expected storage structure: " + buildTypePath);
      return new BuildTypeEntry(buildTypePath, Collections.emptySet(), true);
    }

    Set<BuildEntry> outdatedEntries = new HashSet<>();
    Map<Long, BuildEntry> buildEntries = new HashMap<>();

    for (String fullPath : paths) {
      String path = fullPath;

      if (path.endsWith("/")) {
        path = path.substring(0, path.length() - 1);
      }

      String entryName = path.substring(path.lastIndexOf('/') + 1);

      if (entryName.isEmpty()) {
        continue;
      }

      BuildEntry buildEntry = new BuildEntry(path, entryName);

      if (NUMERIC_BUILD_ID.matcher(entryName).matches()) {
        long id = Long.parseLong(entryName);
        buildEntries.put(id, buildEntry);
      } else {
        outdatedEntries.add(buildEntry);
      }
    }

    LOG.debug("Found " + outdatedEntries + " of non-conforming build entries");

    myServer.getRunningBuilds(null, build -> buildEntries.containsKey(build.getBuildId()))
      .stream()
      .map(Build::getBuildId)
      .forEach(buildEntries::remove);

    myBuildHistory.findEntries(buildEntries.keySet())
      .forEach(build -> buildEntries.remove(build.getBuildId()));

    scannedPaths.add(paths.size());

    outdatedEntries.addAll(buildEntries.values());

    LOG.debug("Found " + outdatedEntries + " of all entries in " + buildTypePath);

    return new BuildTypeEntry(
      buildTypePath,
      outdatedEntries,
      outdatedEntries.size() == paths.size()
    );
  }

  @NotNull
  private List<SProject> gatherProjects(@NotNull SUser user, SProject startingProject) {
    final List<SProject> projects = new ArrayList<>();
    projects.add(startingProject);
    for (SProject subProject : startingProject.getProjects()) {
      if (AuthUtil.hasPermissionToManageProject(user, subProject.getProjectId())) {
        projects.add(subProject);
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
