package jetbrains.buildServer.artifacts.s3.publish;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;
import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.ArtifactsConstants;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.agent.artifacts.AgentArtifactHelper;
import jetbrains.buildServer.artifacts.ArtifactDataInstance;
import jetbrains.buildServer.artifacts.s3.S3PreSignUrlHelper;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.http.HttpUtil;
import jetbrains.buildServer.log.LogUtil;
import jetbrains.buildServer.util.*;
import jetbrains.buildServer.util.amazon.AWSException;
import jetbrains.buildServer.util.filters.Filter;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.methods.FileRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;

import static jetbrains.buildServer.artifacts.s3.S3Constants.*;
import static jetbrains.buildServer.artifacts.s3.S3Util.getBucketName;
import static jetbrains.buildServer.artifacts.s3.S3Util.usePreSignedUrls;

public class S3ArtifactsPublisher implements ArtifactsPublisher {

  private static final String HTTP_AUTH = "/httpAuth";
  private static final String APPLICATION_XML = "application/xml";
  private static final String UTF_8 = "UTF-8";

  private static final Logger LOG = Logger.getInstance(S3ArtifactsPublisher.class.getName());
  private static final String ERROR_PUBLISHING_ARTIFACTS_LIST = "Error publishing artifacts list";

  private final CurrentBuildTracker myTracker;
  private final AgentArtifactHelper myHelper;

  private boolean isDestinationPrepared = false;
  private List<ArtifactDataInstance> myArtifacts = new ArrayList<ArtifactDataInstance>();

  public S3ArtifactsPublisher(@NotNull final AgentArtifactHelper helper,
                              @NotNull final EventDispatcher<AgentLifeCycleListener> dispatcher,
                              @NotNull final CurrentBuildTracker tracker) {
    myHelper = helper;
    myTracker = tracker;
    dispatcher.addListener(new AgentLifeCycleAdapter() {
      @Override
      public void buildStarted(@NotNull AgentRunningBuild runningBuild) {
        isDestinationPrepared = false;
        myArtifacts.clear();
      }

      @Override
      public void afterAtrifactsPublished(@NotNull AgentRunningBuild runningBuild, @NotNull BuildFinishedStatus status) {
        publishArtifactsList(runningBuild);
      }
    });
  }

  @Override
  public int publishFiles(@NotNull final Map<File, String> map) throws ArtifactPublishingFailedException {
    Map<File, String> filteredMap = CollectionsUtil.filterMapByValues(map, new Filter<String>() {
      @Override
      public boolean accept(@NotNull String s) {
        return !s.startsWith(ArtifactsConstants.TEAMCITY_ARTIFACTS_DIR);
      }
    });

    if (!filteredMap.isEmpty()) {
      final AgentRunningBuild build = myTracker.getCurrentBuild();
      Map<String, String> storageSettings = myTracker.getCurrentBuild().getArtifactStorageSettings();
      if (usePreSignedUrls(storageSettings)) {
        String bucketName = getBucketName(storageSettings);
        if (StringUtil.isEmpty(bucketName)) {
          throw new IllegalArgumentException("S3 bucket name must not be empty");
        }
        myArtifacts.addAll(publishFilesWithPreSignedUrls(build, bucketName, filteredMap));
      } else {
        final Map<String, String> validStorageSettings = S3Util.validateParameters(storageSettings);
        myArtifacts.addAll(publishFilesWithS3Client(build, validStorageSettings, filteredMap));
      }
    }

    return filteredMap.size();
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @NotNull
  @Override
  public String getType() {
    return S3_STORAGE_TYPE;
  }

  private List<ArtifactDataInstance> publishFilesWithS3Client(AgentRunningBuild build, Map<String, String> params, final Map<File, String> filesToPublish) {
    final String bucketName = getBucketName(params);
    try {
      prepareDestination(bucketName, params, build);
      final String objectKeyPrefix = getPathPrefix(build);
      final List<ArtifactDataInstance> artifacts = new ArrayList<ArtifactDataInstance>();
      jetbrains.buildServer.util.amazon.S3Util.withTransferManager(params, new jetbrains.buildServer.util.amazon.S3Util.WithTransferManager<Upload>() {
        @NotNull
        @Override
        public Collection<Upload> run(@NotNull final TransferManager transferManager) throws Throwable {
          return CollectionsUtil.convertAndFilterNulls(filesToPublish.entrySet(), new Converter<Upload, Map.Entry<File, String>>() {
            @Override
            public Upload createFrom(@NotNull Map.Entry<File, String> entry) {
              final File file = entry.getKey();
              final String path = entry.getValue();
              final String artifactPath = normalizeArtifactPath(path, file);
              final String objectKey = objectKeyPrefix + artifactPath;

              artifacts.add(ArtifactDataInstance.create(artifactPath, file.length()));

              final ObjectMetadata metadata = new ObjectMetadata();
              metadata.setContentType(S3Util.getContentType(file));
              final PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, objectKey, file)
                .withCannedAcl(CannedAccessControlList.Private)
                .withMetadata(metadata);

              return transferManager.upload(putObjectRequest);
            }
          });
        }
      });
      return artifacts;
    } catch (ArtifactPublishingFailedException t) {
      throw t;
    } catch (Throwable t) {
      final AWSException awsException = new AWSException(t);
      final String details = awsException.getDetails();

      if (StringUtil.isNotEmpty(details)) {
        LOG.warn(details);
        myTracker.getCurrentBuild().getBuildLogger().error(details);
      }

      throw new ArtifactPublishingFailedException(awsException.getMessage(), false, awsException);
    }
  }

  private List<ArtifactDataInstance> publishFilesWithPreSignedUrls(@NotNull AgentRunningBuild build, @NotNull String bucketName, @NotNull Map<File, String> filesToPublish) {
    final Map<File, String> fileToNormalizedArtifactPathMap = new HashMap<File, String>();
    final Map<File, String> fileToS3ObjectKeyMap = new HashMap<File, String>();
    final String s3ObjectKeyPrefix = getPathPrefix(build);

    build.getBuildLogger().message("Artifacts are published to the S3 path " + s3ObjectKeyPrefix + " in the S3 bucket " + bucketName);

    for (Map.Entry<File, String> entry : filesToPublish.entrySet()) {
      String normalizeArtifactPath = normalizeArtifactPath(entry.getValue(), entry.getKey());
      fileToNormalizedArtifactPathMap.put(entry.getKey(), normalizeArtifactPath);
      fileToS3ObjectKeyMap.put(entry.getKey(), s3ObjectKeyPrefix + normalizeArtifactPath);
    }

    final Map<String, URL> preSignedUploadUrls;
    try {
      preSignedUploadUrls = resolveUploadUrls(build, fileToS3ObjectKeyMap.values());
    } catch (IOException e) {
      throw new ArtifactPublishingFailedException(e.getMessage(), false, e);
    }
    final int connectionTimeout = build.getAgentConfiguration().getServerConnectionTimeout();

    return CollectionsUtil.convertAndFilterNulls(filesToPublish.keySet(), new Converter<ArtifactDataInstance, File>() {
      @Override
      public ArtifactDataInstance createFrom(@NotNull File file) {
        String artifactPath = fileToNormalizedArtifactPathMap.get(file);
        URL uploadUrl = preSignedUploadUrls.get(fileToS3ObjectKeyMap.get(file));
        if (uploadUrl == null) {
          throw new ArtifactPublishingFailedException("Failed to publish artifact " + artifactPath + ". Can't get presigned upload url.", false, null);
        }
        HttpClient httpClient = HttpUtil.createHttpClient(connectionTimeout, uploadUrl, null);
        try {
          PutMethod putMethod = new PutMethod(uploadUrl.toString());
          putMethod.setRequestEntity(new FileRequestEntity(file, S3Util.getContentType(file)));
          int responseCode = httpClient.executeMethod(putMethod);
          if (responseCode == 200) {
            LOG.debug(String.format("Successfully upload artifact %s to %s", artifactPath, uploadUrl));
          } else {
            throw new ArtifactPublishingFailedException(String.format("Failed upload artifact %s to %s. Response code received: %d.", artifactPath, uploadUrl, responseCode), false, null);
          }
        } catch (IOException ex) {
          throw new ArtifactPublishingFailedException(ex.getMessage(), false, ex);
        }
        return ArtifactDataInstance.create(artifactPath, file.length());
      }
    });
  }

  private String normalizeArtifactPath(final String path, final File file) {
    if (StringUtil.isEmpty(path)) {
      return file.getName();
    } else {
      return FileUtil.normalizeRelativePath(String.format("%s/%s", path, file.getName()));
    }
  }

  private void prepareDestination(final String bucketName,
                                  final Map<String, String> params,
                                  final AgentRunningBuild build) throws Throwable {
    if (isDestinationPrepared) return;

    S3Util.withS3Client(params, new S3Util.WithS3<Void, Throwable>() {
      @Nullable
      @Override
      public Void run(@NotNull AmazonS3 s3Client) throws Throwable {
        // Minio does not support #doesBucketExistsV2
        // noinspection deprecation
        if (s3Client.doesBucketExist(bucketName)) {
          final String pathPrefix = getPathPrefix(build);
          build.getBuildLogger().message("Artifacts are published to the S3 path " + pathPrefix + " in the S3 bucket " + bucketName);
          isDestinationPrepared = true;
          return null;
        }
        throw new ArtifactPublishingFailedException("Target S3 artifact bucket " + bucketName + " doesn't exist", false, null);
      }
    });
  }

  private void publishArtifactsList(AgentRunningBuild build) {
    if (!myArtifacts.isEmpty()) {
      final String pathPrefix = getPathPrefix(build);
      try {
        myHelper.publishArtifactList(myArtifacts, CollectionsUtil.asMap(S3_PATH_PREFIX_ATTR, pathPrefix));
      } catch (IOException e) {
        build.getBuildLogger().error(ERROR_PUBLISHING_ARTIFACTS_LIST + ": " + e.getMessage());
        LOG.warnAndDebugDetails(ERROR_PUBLISHING_ARTIFACTS_LIST + "for build " + LogUtil.describe(build), e);
      }
    }
  }

  @NotNull
  private String getPathPrefix(@NotNull AgentRunningBuild build) {
    final List<String> pathSegments = new ArrayList<String>();
    pathSegments.add(build.getSharedConfigParameters().get(ServerProvidedProperties.TEAMCITY_PROJECT_ID_PARAM));
    pathSegments.add(build.getBuildTypeExternalId());
    pathSegments.add(Long.toString(build.getBuildId()));
    return StringUtil.join("/", pathSegments) + "/";
  }

  @NotNull
  private static Map<String, URL> resolveUploadUrls(AgentRunningBuild build, @NotNull Collection<String> s3ObjectKeys) throws IOException {
    BuildAgentConfiguration agentConfiguration = build.getAgentConfiguration();
    String targetUrl = agentConfiguration.getServerUrl() + HTTP_AUTH + ARTEFACTS_S3_UPLOAD_PRESIGN_URLS_HTML;
    int connectionTimeout = agentConfiguration.getServerConnectionTimeout();
    UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(build.getAccessUser(), build.getAccessCode());
    HttpClient httpClient = HttpUtil.createHttpClient(connectionTimeout, new URL(targetUrl), credentials);
    PostMethod post = new PostMethod(targetUrl);
    post.setRequestEntity(new StringRequestEntity(S3PreSignUrlHelper.writeS3ObjectKeys(s3ObjectKeys), APPLICATION_XML, UTF_8));
    post.setDoAuthentication(true);
    int responseCode = httpClient.executeMethod(post);
    if (responseCode != 200) {
      LOG.debug("Failed resolving S3 pre-signed URL for build " + build.describe(false) + " . Response code " + responseCode);
      return Collections.emptyMap();
    }
    return S3PreSignUrlHelper.readPreSignUrlMapping(post.getResponseBodyAsString());
  }
}
