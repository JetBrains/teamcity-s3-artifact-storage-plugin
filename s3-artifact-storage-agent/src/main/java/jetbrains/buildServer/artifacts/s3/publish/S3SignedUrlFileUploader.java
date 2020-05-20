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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.ArtifactPublishingFailedException;
import jetbrains.buildServer.artifacts.ArtifactDataInstance;
import jetbrains.buildServer.artifacts.s3.S3PreSignUrlHelper;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.artifacts.s3.retry.LoggingRetrier;
import jetbrains.buildServer.artifacts.s3.retry.Retrier;
import jetbrains.buildServer.artifacts.s3.retry.RetrierExponentialDelay;
import jetbrains.buildServer.artifacts.s3.retry.RetrierImpl;
import jetbrains.buildServer.http.HttpUtil;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.util.StringUtil;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.FileRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.artifacts.s3.S3Constants.ARTEFACTS_S3_UPLOAD_PRESIGN_URLS_HTML;

public class S3SignedUrlFileUploader implements S3FileUploader {

  private static final int DEFAULT_TOTAL_CONNECTIONS = MultiThreadedHttpConnectionManager.DEFAULT_MAX_TOTAL_CONNECTIONS;
  private static final Logger LOG = Logger.getInstance(S3SignedUrlFileUploader.class.getName());
  private static final String HTTP_AUTH = "/httpAuth";
  private static final String APPLICATION_XML = "application/xml";
  private static final String UTF_8 = "UTF-8";

  private final ExecutorService myExecutorService = jetbrains.buildServer.util.amazon.S3Util.createDefaultExecutorService();

  @NotNull
  private static String targetUrl(@NotNull final AgentRunningBuild build) {
    return build.getAgentConfiguration().getServerUrl() + HTTP_AUTH + ARTEFACTS_S3_UPLOAD_PRESIGN_URLS_HTML;
  }

  @NotNull
  private static Map<String, URL> fetchUploadUrlFromServer(@NotNull final HttpClient httpClient,
                                                           @NotNull final AgentRunningBuild build,
                                                           @NotNull final Collection<String> s3ObjectKeys) throws IOException {
    try {
      final PostMethod post = new PostMethod(targetUrl(build));
      post.addRequestHeader("User-Agent", "TeamCity Agent");
      post.setRequestEntity(new StringRequestEntity(S3PreSignUrlHelper.writeS3ObjectKeys(s3ObjectKeys), APPLICATION_XML, UTF_8));
      post.setDoAuthentication(true);
      final String responseBody = HttpClientCloseUtil.executeReleasingConnectionAndReadResponseBody(httpClient, post);
      return S3PreSignUrlHelper.readPreSignUrlMapping(responseBody);
    } catch (HttpClientCloseUtil.HttpErrorCodeException e) {
      LOG.debug("Failed resolving S3 pre-signed URL for build " + build.describe(false) + " . Response code " + e.getResponseCode());
      return Collections.emptyMap();
    }
  }

  @Nullable
  private static URL fetchUploadUrlFromServer(@NotNull final HttpClient httpClient, @NotNull final AgentRunningBuild build, @NotNull final String s3ObjectKey) {
    try {
      return fetchUploadUrlFromServer(httpClient, build, Collections.singleton(s3ObjectKey)).get(s3ObjectKey);
    } catch (IOException e) {
      throw new ArtifactPublishingFailedException(e.getMessage(), false, e);
    }
  }

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
    final int numberOfRetries = S3Util.getNumberOfRetries(build.getSharedConfigParameters());
    final int retryDelay = S3Util.getRetryDelayInMs(build.getSharedConfigParameters());

    for (Map.Entry<File, String> entry : filesToPublish.entrySet()) {
      String normalizeArtifactPath = S3Util.normalizeArtifactPath(entry.getValue(), entry.getKey());
      fileToNormalizedArtifactPathMap.put(entry.getKey(), normalizeArtifactPath);
      fileToS3ObjectKeyMap.put(entry.getKey(), pathPrefix + normalizeArtifactPath);
    }

    final ConcurrentLinkedQueue<ArtifactDataInstance> artifacts = new ConcurrentLinkedQueue<ArtifactDataInstance>();
    final HttpClient awsHttpClient = createPooledHttpClient(build);
    final HttpClient tcServerClient = createPooledHttpClientToTCServer(build);
    final Retrier retrier = new RetrierImpl(numberOfRetries)
      .registerListener(new LoggingRetrier(LOG))
      .registerListener(new RetrierExponentialDelay(retryDelay));

    final List<Callable<Void>> uploadTasks = CollectionsUtil.convertAndFilterNulls(filesToPublish.keySet(), new Converter<Callable<Void>, File>() {
      @Override
      public Callable<Void> createFrom(@NotNull final File file) {
        return new Callable<Void>() {
          @Override
          public Void call() {
            return retrier.execute(new Callable<Void>() {
              @Override
              public String toString() {
                return "publishing file '" + file.getName() + "'";
              }

              @Override
              public Void call() throws IOException {
                if (!file.exists()) {
                  build.getBuildLogger().warning("Artifact \"" + file.getAbsolutePath() + "\" does not exist and will not be published to the server");
                  return null;
                }
                final String artifactPath = fileToNormalizedArtifactPathMap.get(file);
                final URL uploadUrl = fetchUploadUrlFromServer(tcServerClient, build, fileToS3ObjectKeyMap.get(file));
                try {
                  if (uploadUrl == null) {
                    final String message = "Failed to publish artifact " + artifactPath + ". Can't get presigned upload url.";
                    LOG.info(message);
                    throw new IOException(message);
                  }
                  uploadArtifact(artifactPath, uploadUrl, file, awsHttpClient);
                  artifacts.add(ArtifactDataInstance.create(artifactPath, file.length()));
                  return null;
                } catch (IOException e) {
                  if (uploadUrl != null) {
                    throw new IOException(e.getMessage() + " upload url: [" + uploadUrl + "]", e);
                  } else {
                    throw e;
                  }
                }
              }
            });
          }
        };
      }
    });

    try {
      final StringBuilder exceptions = new StringBuilder();
      try {
        final List<Future<Void>> futures = myExecutorService.invokeAll(uploadTasks);
        for (final Future<Void> future : futures) {
          try {
            future.get();
          } catch (Exception e) {
            exceptions.append("\n").append(e.getMessage());
          }
        }
      } catch (Exception e) {
        throw new ArtifactPublishingFailedException(String.format("Failed to upload artifacts into bucket %s: %s", bucketName, e.getMessage()), false, null);
      }

      if (exceptions.length() > 0) {
        throw new ArtifactPublishingFailedException(String.format("Failed to upload artifacts into bucket %s: %s", bucketName, exceptions), false, null);
      }
    } finally {
      HttpClientCloseUtil.shutdown(awsHttpClient, tcServerClient);
    }

    return artifacts;
  }

  private void uploadArtifact(@NotNull final String artifactPath, @NotNull final URL uploadUrl, @NotNull final File file, @NotNull final HttpClient awsHttpClient)
    throws IOException {
    try {
      final PutMethod putMethod = new PutMethod(uploadUrl.toString());
      putMethod.addRequestHeader("User-Agent", "TeamCity Agent");
      putMethod.setRequestEntity(new FileRequestEntity(file, S3Util.getContentType(file)));
      HttpClientCloseUtil.executeAndReleaseConnection(awsHttpClient, putMethod);
      LOG.debug(String.format("Successfully uploaded artifact %s to %s", artifactPath, uploadUrl));
    } catch (HttpClientCloseUtil.HttpErrorCodeException e) {
      final String msg;
      if (e.getResponseCode() == HttpStatus.SC_FORBIDDEN) {
        msg = "Failed to upload artifact " + artifactPath + ": received response code HTTP 403. Ensure that the credentials in S3 storage profile are correct.";
      } else {
        msg = "Failed to upload artifact " + artifactPath + " to " + uploadUrl + ": received response code HTTP " + e.getResponseCode() + ".";
      }
      LOG.info(msg);
      throw new IOException(msg);
    }
  }

  @NotNull
  private HttpClient createPooledHttpClient(final AgentRunningBuild build) {
    final int connectionTimeout = build.getAgentConfiguration().getServerConnectionTimeout();
    final HttpClient httpClient = HttpUtil.createHttpClient(connectionTimeout);
    final HttpConnectionManager httpConnectionManager = createMultiThreadedHttpConnectionManager(connectionTimeout);
    httpClient.setHttpConnectionManager(httpConnectionManager);
    return httpClient;
  }

  @NotNull
  private HttpClient createPooledHttpClientToTCServer(@NotNull final AgentRunningBuild build) {
    final int connectionTimeout = build.getAgentConfiguration().getServerConnectionTimeout();
    final HttpClient httpClient;
    try {
      httpClient = HttpUtil.createHttpClient(connectionTimeout, new URL(targetUrl(build)), new UsernamePasswordCredentials(build.getAccessUser(), build.getAccessCode()));
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
    final HttpConnectionManager httpConnectionManager = createMultiThreadedHttpConnectionManager(connectionTimeout);
    httpClient.setHttpConnectionManager(httpConnectionManager);
    return httpClient;
  }

  @NotNull
  private HttpConnectionManager createMultiThreadedHttpConnectionManager(final int connectionTimeout) {
    final HttpConnectionManager threadSafeConnectionManager = HttpUtil.createMultiThreadedHttpConnectionManager(connectionTimeout);
    final int maxConnections = TeamCityProperties.getInteger("teamcity.s3.artifactUploader.maxTotalConnections", DEFAULT_TOTAL_CONNECTIONS);
    threadSafeConnectionManager.getParams().setMaxTotalConnections(maxConnections);
    threadSafeConnectionManager.getParams().setDefaultMaxConnectionsPerHost(maxConnections);
    return threadSafeConnectionManager;
  }

  /**
   * Releasing all resources created by <code>S3SignedUrlFileUploader</code> before it
   * is being garbage collected.
   */
  @Override
  protected void finalize() {
    myExecutorService.shutdown();
  }
}
