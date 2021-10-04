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
import jetbrains.buildServer.http.HttpUtil;
import jetbrains.buildServer.transport.AgentServerSharedErrorMessages;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.amazon.retry.RecoverableException;
import org.apache.commons.httpclient.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitrii Bogdanov
 */
public final class HttpClientUtil {
  private static final Logger LOG = Logger.getInstance(HttpClientUtil.class.getName());
  private static final Converter<String, HttpMethod> RESPONSE_BODY_EXTRACTING_CONVERTER = source -> {
    try {
      return source.getResponseBodyAsString();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  };

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

  public static void executeAndReleaseConnection(@NotNull final HttpClient client,
                                                 @NotNull final HttpMethod method) throws IOException {
    executeAndReleaseConnectionInternal(client, method, null);
  }

  public static String executeReleasingConnectionAndReadResponseBody(@NotNull final HttpClient client,
                                                                     @NotNull final HttpMethod method) throws IOException {
    return executeAndReleaseConnectionInternal(client, method, RESPONSE_BODY_EXTRACTING_CONVERTER);
  }

  private static <T> T executeAndReleaseConnectionInternal(@NotNull final HttpClient client,
                                                           @NotNull final HttpMethod method,
                                                           @Nullable final Converter<T, HttpMethod> resultConverter) throws IOException {
    try {
      final int code = client.executeMethod(method);
      if (code != 200) {
        String response = null;
        try {
          response = method.getResponseBodyAsString();
        } catch (Exception e) {
          LOG.infoAndDebugDetails(() -> "Got exception while trying to get error response: " + e.getMessage(), e);
        }
        throw new HttpErrorCodeException(code, response);
      }
      if (resultConverter != null) {
        return resultConverter.createFrom(method);
      } else {
        return null;
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
    private final boolean isBuildFinishedReason;

    public HttpErrorCodeException(final int responseCode, @Nullable final String responseString) {
      super("Got response code " + responseCode + ". Response: '" + StringUtil.emptyIfNull(responseString) + "'");
      myResponseCode = responseCode;
      myResponse = responseString;
      isBuildFinishedReason = responseString != null && responseString.contains(AgentServerSharedErrorMessages.buildIsAlreadyFinishedOrDoesNotExist());
    }

    public int getResponseCode() {
      return myResponseCode;
    }

    @Nullable
    public String getResponse() {
      return myResponse;
    }

    public boolean isBuildFinishedReason() {
      return isBuildFinishedReason;
    }

    @Override
    public boolean isRecoverable() {
      if (isBuildFinishedReason()) {
        return false;
      }
      return myResponseCode == HttpStatus.SC_INTERNAL_SERVER_ERROR ||
             myResponseCode == HttpStatus.SC_BAD_GATEWAY ||
             myResponseCode == HttpStatus.SC_GATEWAY_TIMEOUT ||
             (myResponse != null && (StringUtil.containsIgnoreCase(myResponse, "timeout")
                                     || StringUtil.containsIgnoreCase(myResponse, "retry")
                                     || StringUtil.containsIgnoreCase(myResponse, "connection refused")));
    }
  }
}
