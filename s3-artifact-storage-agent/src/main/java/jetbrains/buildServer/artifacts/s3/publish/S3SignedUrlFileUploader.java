package jetbrains.buildServer.artifacts.s3.publish;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.ArtifactPublishingFailedException;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.artifacts.ArtifactDataInstance;
import jetbrains.buildServer.artifacts.s3.S3PreSignUrlHelper;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.http.HttpUtil;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.util.StringUtil;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.methods.FileRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static jetbrains.buildServer.artifacts.s3.S3Constants.ARTEFACTS_S3_UPLOAD_PRESIGN_URLS_HTML;

public class S3SignedUrlFileUploader implements S3FileUploader {

  private static final Logger LOG = Logger.getInstance(S3SignedUrlFileUploader.class.getName());
  private static final String HTTP_AUTH = "/httpAuth";
  private static final String APPLICATION_XML = "application/xml";
  private static final String UTF_8 = "UTF-8";

  @NotNull
  @Override
  public Collection<ArtifactDataInstance> publishFiles(@NotNull final AgentRunningBuild build,
                                                       @NotNull final String pathPrefix,
                                                       @NotNull final Map<File, String> filesToPublish) {
    final Map<String, String> settings = build.getArtifactStorageSettings();
    final String bucketName = S3Util.getBucketName(settings);
    if (StringUtil.isEmpty(bucketName)) {
      throw new IllegalArgumentException("S3 bucket name must not be empty");
    }

    final Map<File, String> fileToNormalizedArtifactPathMap = new HashMap<File, String>();
    final Map<File, String> fileToS3ObjectKeyMap = new HashMap<File, String>();

    build.getBuildLogger().message("Artifacts are published to the S3 path " + pathPrefix + " in the S3 bucket " + bucketName);

    for (Map.Entry<File, String> entry : filesToPublish.entrySet()) {
      String normalizeArtifactPath = S3Util.normalizeArtifactPath(entry.getValue(), entry.getKey());
      fileToNormalizedArtifactPathMap.put(entry.getKey(), normalizeArtifactPath);
      fileToS3ObjectKeyMap.put(entry.getKey(), pathPrefix + normalizeArtifactPath);
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
