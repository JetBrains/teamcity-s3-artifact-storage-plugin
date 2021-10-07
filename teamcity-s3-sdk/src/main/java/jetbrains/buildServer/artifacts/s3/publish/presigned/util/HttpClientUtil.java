/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

package jetbrains.buildServer.artifacts.s3.publish.presigned.util;

import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import jetbrains.buildServer.artifacts.s3.publish.errors.HttpMethodResponseAdapter;
import jetbrains.buildServer.artifacts.s3.publish.errors.HttpResponseErrorHandler;
import jetbrains.buildServer.http.HttpUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.amazon.retry.RecoverableException;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitrii Bogdanov
 */
public final class HttpClientUtil {
  @NotNull
  private static final Logger LOG = Logger.getInstance(HttpClientUtil.class.getName());

  private HttpClientUtil() {
  }

  public static void shutdown(@NotNull final HttpClient... httpClients) {
    for (final HttpClient httpClient : httpClients) {
      if (httpClient.getHttpConnectionManager() instanceof MultiThreadedHttpConnectionManager) {
        try {
          ((MultiThreadedHttpConnectionManager)httpClient.getHttpConnectionManager()).shutdown();
        } catch (Exception e) {
          LOG.infoAndDebugDetails("Got exception while shutting down httpClient " + httpClient + ".", e);
        }
      }
    }
  }

  public static String executeAndReleaseConnection(@NotNull final HttpClient client,
                                                   @NotNull final HttpMethod method,
                                                   @NotNull final HttpResponseErrorHandler errorHandler) throws IOException {
    return executeAndReleaseConnectionInternal(client, method, errorHandler);
  }

  private static String executeAndReleaseConnectionInternal(@NotNull final HttpClient client,
                                                            @NotNull final HttpMethod method,
                                                            @NotNull final HttpResponseErrorHandler errorHandler) throws IOException {
    try {
      final int code = client.executeMethod(method);
      if (code != 200) {
        throw errorHandler.handle(new HttpMethodResponseAdapter(method));
      }
      try {
        return method.getResponseBodyAsString();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    } finally {
      try {
        method.releaseConnection();
      } catch (Exception e) {
        LOG.infoAndDebugDetails("Got exception while trying to release connection for " + method, e);
      }
    }
  }

  @NotNull
  public static HttpConnectionManager createConnectionManager(final int connectionTimeout, final int maxConnections) {
    final HttpConnectionManager threadSafeConnectionManager = HttpUtil.createMultiThreadedHttpConnectionManager(connectionTimeout);
    threadSafeConnectionManager.getParams().setMaxTotalConnections(maxConnections);
    threadSafeConnectionManager.getParams().setDefaultMaxConnectionsPerHost(maxConnections);
    return threadSafeConnectionManager;
  }

  public static class HttpErrorCodeException extends RecoverableException {
    private final int myResponseCode;
    @Nullable
    private final String myResponse;
    private final boolean myIsRecoverable;
    private final boolean myIsUploadInterrupted;

    public HttpErrorCodeException(final int responseCode, @Nullable final String responseString, final boolean isRecoverable) {
      this(responseCode, responseString, isRecoverable, false);
    }

    public HttpErrorCodeException(final int responseCode, @Nullable final String responseString, final boolean isRecoverable, final boolean isUploadInterrupted) {
      super("Got response code " + responseCode + ". Response: '" + StringUtil.emptyIfNull(responseString) + "'");
      myResponseCode = responseCode;
      myResponse = responseString;
      myIsRecoverable = isRecoverable;
      myIsUploadInterrupted = isUploadInterrupted;
    }

    public int getResponseCode() {
      return myResponseCode;
    }

    @Nullable
    public String getResponse() {
      return myResponse;
    }

    @Override
    public boolean isRecoverable() {
      return myIsRecoverable;
    }

    public boolean isUploadInterrupted() {
      return myIsUploadInterrupted;
    }
  }
}
