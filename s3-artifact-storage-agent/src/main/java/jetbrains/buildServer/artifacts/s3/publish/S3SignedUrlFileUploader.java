/*
 * Copyright 2000-2020 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.artifacts.s3.publish;

import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.ArtifactPublishingFailedException;
import jetbrains.buildServer.artifacts.ArtifactDataInstance;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.http.HttpUserAgent;
import jetbrains.buildServer.http.HttpUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.amazon.S3Util.S3AdvancedConfiguration;
import jetbrains.buildServer.util.amazon.retry.AbstractRetrierEventListener;
import jetbrains.buildServer.util.amazon.retry.Retrier;
import jetbrains.buildServer.util.amazon.retry.impl.ExponentialDelayListener;
import jetbrains.buildServer.util.amazon.retry.impl.LoggingRetrierListener;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.FileRequestEntity;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.http.HttpHeaders;
import org.jetbrains.annotations.NotNull;

public class S3SignedUrlFileUploader extends S3FileUploader {
  @NotNull
  private static final Logger LOGGER = Logger.getInstance(S3SignedUrlFileUploader.class.getName());

  @NotNull
  @Override
  public Collection<ArtifactDataInstance> publish(@NotNull final AgentRunningBuild build,
                                                  @NotNull final String pathPrefix,
                                                  @NotNull final Map<File, String> filesToPublish) {
    final Map<String, String> settings = build.getArtifactStorageSettings();
    final String bucketName = S3Util.getBucketName(settings);
    if (StringUtil.isEmpty(bucketName)) {
      throw new IllegalArgumentException("S3 bucket name must not be empty");
    }

    final Map<File, String> fileToNormalizedArtifactPathMap = new HashMap<>();
    final Map<File, String> fileToS3ObjectKeyMap = new HashMap<>();

    final S3AdvancedConfiguration s3Config = configuration(build.getSharedConfigParameters());

    for (Map.Entry<File, String> entry : filesToPublish.entrySet()) {
      final String normalizeArtifactPath = S3Util.normalizeArtifactPath(entry.getValue(), entry.getKey());
      fileToNormalizedArtifactPathMap.put(entry.getKey(), normalizeArtifactPath);
      fileToS3ObjectKeyMap.put(entry.getKey(), pathPrefix + normalizeArtifactPath);
    }

    final Retrier retrier = Retrier.withRetries(s3Config.getRetriesNum())
                                   .registerListener(new LoggingRetrierListener(LOGGER))
                                   .registerListener(new AbstractRetrierEventListener() {
                                     @Override
                                     public <T> void onFailure(@NotNull Callable<T> callable, int retry, @NotNull Exception e) {
                                       super.onFailure(callable, retry, e);
                                       final String retryLogPart = retry == 0 ? "" : " after " + retry + "retry";
                                       build.getBuildLogger().message("Uploading of " + callable + " failed with exception " + e.getMessage() + retryLogPart);
                                     }
                                   })
                                   .registerListener(new ExponentialDelayListener(s3Config.getRetryDelay()));

    try (final CloseableForkJoinPoolAdapter forkJoinPool = new CloseableForkJoinPoolAdapter(s3Config.getNThreads());
         final CloseableHttpClientWrapper awsClient = createAwsClient(s3Config);
         final SignedUrlProvider signedUrlProvider = new SignedUrlProvider(teamcityConnectionConfiguration(build), fileToS3ObjectKeyMap.values())) {
      return filesToPublish.keySet().stream().map(file -> forkJoinPool.submit(() -> retrier.execute(new Callable<ArtifactDataInstance>() {
        @Override
        public String toString() {
          return "publishing artifact '" + file.getName() + "'";
        }

        @Override
        public ArtifactDataInstance call() throws Exception {
          if (!file.exists()) {
            build.getBuildLogger().warning("File \"" + file.getAbsolutePath() + "\" does not exist and will not be published to the server");
            throw new ArtifactPublishingFailedException("File " + file.getAbsolutePath() + " does not exist", false, null);
          }
          final String artifactPath = fileToNormalizedArtifactPathMap.get(file);
          final URL targetUrl = signedUrlProvider.getUrl(fileToS3ObjectKeyMap.get(file));
          uploadArtifact(artifactPath, targetUrl, file, awsClient.getHttpClient());
          return ArtifactDataInstance.create(artifactPath, file.length());
        }
      }))).map(future -> {
        try {
          return future.get();
        } catch (InterruptedException | ExecutionException e) {
          throw new ArtifactPublishingFailedException("Got exception while uploading artefact", false, e);
        }
      }).collect(Collectors.toList());
    }
  }

  @NotNull
  private SignedUrlProvider.TeamCityConnectionConfiguration teamcityConnectionConfiguration(@NotNull AgentRunningBuild build) {
    return new SignedUrlProvider.TeamCityConnectionConfiguration(build.getAgentConfiguration().getServerUrl(),
                                                                 build.getAccessUser(),
                                                                 build.getAccessCode(),
                                                                 build.getAgentConfiguration().getServerConnectionTimeout());
  }

  private void uploadArtifact(@NotNull final String artifactPath,
                              @NotNull final URL uploadUrl,
                              @NotNull final File file,
                              @NotNull final HttpClient httpClient) throws IOException {
    try {
      final PutMethod putMethod = new PutMethod(uploadUrl.toString());
      putMethod.setRequestHeader(HttpHeaders.USER_AGENT, HttpUserAgent.getUserAgent());
      putMethod.setRequestEntity(new FileRequestEntity(file, S3Util.getContentType(file)));
      HttpClientUtil.executeAndReleaseConnection(httpClient, putMethod);
      LOGGER.debug(String.format("Successfully uploaded artifact %s to %s", artifactPath, uploadUrl));
    } catch (HttpClientUtil.HttpErrorCodeException e) {
      final String msg;
      if (e.getResponseCode() == HttpStatus.SC_FORBIDDEN) {
        msg = "Failed to upload artifact " + artifactPath + ": received response code HTTP 403. Ensure that the credentials in S3 storage profile are correct.";
      } else {
        msg = "Failed to upload artifact " + artifactPath + " to " + uploadUrl + ": received response code HTTP " + e.getResponseCode() + ".";
      }
      LOGGER.info(msg);
      throw new IOException(msg);
    }
  }

  @NotNull
  private CloseableHttpClientWrapper createAwsClient(@NotNull final S3AdvancedConfiguration advancedConfiguration) {
    final HttpClient httpClient = HttpUtil.createHttpClient(advancedConfiguration.getConnectionTimeout());
    final HttpConnectionManager httpConnectionManager = HttpClientUtil.createConnectionManager(advancedConfiguration.getConnectionTimeout(), advancedConfiguration.getNThreads());
    httpClient.setHttpConnectionManager(httpConnectionManager);
    return new CloseableHttpClientWrapper(httpClient);
  }
}
