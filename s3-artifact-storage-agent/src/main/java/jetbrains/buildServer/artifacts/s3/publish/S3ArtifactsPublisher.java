package jetbrains.buildServer.artifacts.s3.publish;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;
import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.ArtifactsConstants;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.agent.artifacts.AgentArtifactHelper;
import jetbrains.buildServer.artifacts.ArtifactDataInstance;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.http.HttpUtil;
import jetbrains.buildServer.log.LogUtil;
import jetbrains.buildServer.util.*;
import jetbrains.buildServer.util.amazon.AWSClients;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import jetbrains.buildServer.util.amazon.AWSException;
import jetbrains.buildServer.util.filters.Filter;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.FileRequestEntity;
import org.apache.commons.httpclient.methods.PutMethod;
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

  private static final Logger LOG = Logger.getInstance(S3ArtifactsPublisher.class.getName());
  private static final String ERROR_PUBLISHING_ARTIFACTS_LIST = "Error publishing artifacts list";

  private final CurrentBuildTracker myTracker;
  private final S3PreSignedUrlResolver myS3PreSignedUrlResolver;
  private final AgentArtifactHelper myHelper;

  private boolean isDestinationPrepared = false;
  private List<ArtifactDataInstance> myArtifacts = new ArrayList<ArtifactDataInstance>();

  public S3ArtifactsPublisher(@NotNull final AgentArtifactHelper helper,
                              @NotNull final EventDispatcher<AgentLifeCycleListener> dispatcher,
                              @NotNull final CurrentBuildTracker tracker,
                              @NotNull final S3PreSignedUrlResolver s3PreSignedUrlResolver) {
    myHelper = helper;
    myTracker = tracker;
    myS3PreSignedUrlResolver = s3PreSignedUrlResolver;
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
      final Map<String, String> params = getPublisherParameters();
      if(usePreSignedUrls(params)){
        myArtifacts.addAll(publishFilesWithPreSignedUrls(build, filteredMap));
      } else {
        myArtifacts.addAll(publishFilesWithS3Client(build, params, filteredMap));
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
      final String objectKeyPrefix = getS3ObjectKeyPrefixProperty(build);
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

              return transferManager.upload(new PutObjectRequest(
                bucketName,
                objectKey,
                file).withCannedAcl(CannedAccessControlList.Private));
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

  private List<ArtifactDataInstance> publishFilesWithPreSignedUrls(final AgentRunningBuild build, Map<File, String> filesToPublish) {
    final Map<File, String> fileToNormalizedArtifactPathMap = new HashMap<File, String>();
    final Map<File, String> fileToS3ObjectKeyMap = new HashMap<File, String>();
    final String s3ObjectKeyPrefix = getS3ObjectKeyPrefixProperty(build);

    for (Map.Entry<File, String> entry : filesToPublish.entrySet()){
      String normalizeArtifactPath = normalizeArtifactPath(entry.getValue(), entry.getKey());
      fileToNormalizedArtifactPathMap.put(entry.getKey(), normalizeArtifactPath);
      fileToS3ObjectKeyMap.put(entry.getKey(), s3ObjectKeyPrefix + normalizeArtifactPath);
    }

    final Map<String, URL> preSignedUploadUrls = myS3PreSignedUrlResolver.resolveUploadUrls(fileToS3ObjectKeyMap.values());
    final int connectionTimeout = build.getAgentConfiguration().getServerConnectionTimeout();

    return CollectionsUtil.convertAndFilterNulls(filesToPublish.keySet(), new Converter<ArtifactDataInstance, File>() {
      @Override
      public ArtifactDataInstance createFrom(@NotNull File file) {
        String artifactPath = fileToNormalizedArtifactPathMap.get(file);
        URL uploadUrl = preSignedUploadUrls.get(fileToS3ObjectKeyMap.get(file));
        if(uploadUrl == null){
          throw new ArtifactPublishingFailedException("Failed to publish artifact " + artifactPath + ". Can't get presigned upload url.", false, null);
        }
        HttpClient httpClient = HttpUtil.createHttpClient(connectionTimeout, uploadUrl, null);
        try {
          PutMethod putMethod = new PutMethod();
          putMethod.setRequestEntity(new FileRequestEntity(file, "application/octet-stream"));
          int responseCode = httpClient.executeMethod(putMethod);
          if(responseCode == 200){
            LOG.debug(String.format("Succesfully upload artifact %s to %s", artifactPath, uploadUrl));
          } else{
            throw new ArtifactPublishingFailedException(String.format("Failed upload artifact %s to %s. Response code received: %d.", artifactPath, uploadUrl, responseCode), false, null);
          }
        } catch (IOException ex){
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

    AWSCommonParams.withAWSClients(params, new AWSCommonParams.WithAWSClients<Void, Throwable>() {
      @Nullable
      @Override
      public Void run(@NotNull AWSClients awsClients) throws Throwable {
        final AmazonS3Client s3Client = awsClients.createS3Client();
        if (s3Client.doesBucketExist(bucketName)) {
          String pathPrefix = getPathPrefix(build);
          if (s3Client.doesObjectExist(bucketName, pathPrefix)) {
            build.getBuildLogger().warning("Default target S3 artifact path " + pathPrefix + " already exists in the S3 bucket " + bucketName + ", will use a custom path");
            pathPrefix = pathPrefix + "_" + System.currentTimeMillis();
          }
          pathPrefix = pathPrefix + "/";
          build.getBuildLogger().message("Artifacts are published to the S3 path " + pathPrefix + " in the S3 bucket " + bucketName);
          build.addSharedSystemProperty(S3_PATH_PREFIX_SYSTEM_PROPERTY, pathPrefix);
          isDestinationPrepared = true;
          return null;
        }
        throw new ArtifactPublishingFailedException("Target S3 artifact bucket " + bucketName + " doesn't exist", false, null);
      }
    });
  }

  private void publishArtifactsList(AgentRunningBuild build) {
    if (!myArtifacts.isEmpty()) {
      final String pathPrefix = getS3ObjectKeyPrefixProperty(build);
      try {
        myHelper.publishArtifactList(myArtifacts, CollectionsUtil.asMap(S3_PATH_PREFIX_ATTR, pathPrefix));
      } catch (IOException e) {
        build.getBuildLogger().error(ERROR_PUBLISHING_ARTIFACTS_LIST + ": " + e.getMessage());
        LOG.warnAndDebugDetails(ERROR_PUBLISHING_ARTIFACTS_LIST + "for build " + LogUtil.describe(build), e);
      }
    }
  }

  @NotNull
  private Map<String, String> getPublisherParameters() {
    return S3Util.validateParameters(myTracker.getCurrentBuild().getArtifactStorageSettings());
  }

  @NotNull
  private String getS3ObjectKeyPrefixProperty(@NotNull AgentRunningBuild build) throws ArtifactPublishingFailedException {
    final String prefix = build.getSharedBuildParameters().getSystemProperties().get(S3_PATH_PREFIX_SYSTEM_PROPERTY);
    if (StringUtil.isEmptyOrSpaces(prefix)) {
      throw new ArtifactPublishingFailedException("No " + S3_PATH_PREFIX_SYSTEM_PROPERTY + " build system property found", false, null);
    }
    return prefix;
  }

  @NotNull
  private String getPathPrefix(@NotNull AgentRunningBuild build) {
    final List<String> pathSegments = new ArrayList<String>();

    // Try to get overriden path prefix
    final String pathPrefix = build.getSharedConfigParameters().get(S3_PATH_PREFIX_SYSTEM_PROPERTY);
    if (StringUtil.isEmptyOrSpaces(pathPrefix)) {
      // Set default path prefix
      pathSegments.add(build.getSharedConfigParameters().get(ServerProvidedProperties.TEAMCITY_PROJECT_ID_PARAM));
      pathSegments.add(build.getBuildTypeExternalId());
      pathSegments.add(Long.toString(build.getBuildId()));
    } else {
      final String[] segments = pathPrefix
        .trim()
        .replace('\\', '/')
        .split("/");
      for (String segment : segments) {
        if (StringUtil.isEmptyOrSpaces(segment)) continue;
        pathSegments.add(segment);
      }
    }

    return StringUtil.join("/", pathSegments);
  }
}
