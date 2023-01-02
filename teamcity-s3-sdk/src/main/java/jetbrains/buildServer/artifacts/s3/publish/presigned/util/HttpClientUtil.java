/*
 * Copyright 2000-2022 JetBrains s.r.o.
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jetbrains.buildServer.artifacts.s3.publish.errors.HttpResponseErrorHandler;
import jetbrains.buildServer.artifacts.s3.publish.errors.HttpResponseResponseAdapter;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.amazon.retry.RecoverableException;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitrii Bogdanov
 */
public final class HttpClientUtil {
  @NotNull
  private static final Logger LOG = Logger.getInstance(HttpClientUtil.class.getName());
  private static final ExecutorService myExecutorService = Executors.newCachedThreadPool();

  private HttpClientUtil() {
  }

  public static void shutdown(CloseableHttpClient... httpClients) {
    for (final CloseableHttpClient httpClient : httpClients) {
      try {
        httpClient.close();
      } catch (IOException e) {
        LOG.infoAndDebugDetails("Got exception while shutting down httpClient " + httpClient + ".", e);
      }
    }
  }

  public static CompletableFuture<HttpResponse> executeAndReleaseConnection(@NotNull final CloseableHttpClient client,
                                                                            @NotNull final HttpRequestBase method,
                                                                            @NotNull final HttpResponseErrorHandler errorHandler) {
    return executeAndReleaseConnection(client, method, errorHandler, myExecutorService);
  }

  public static CompletableFuture<HttpResponse> executeAndReleaseConnection(@NotNull final CloseableHttpClient client,
                                                                            @NotNull final HttpRequestBase method,
                                                                            @NotNull final HttpResponseErrorHandler errorHandler,
                                                                            @NotNull ExecutorService executorService) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        HttpResponse response = client.execute(method);
        if (response.getStatusLine().getStatusCode() != 200) {
          throw errorHandler.handle(new HttpResponseResponseAdapter(response));
        }
        return response;
      } catch (IOException e) {
        throw new RuntimeException(e);
      } finally {
        try {
          method.releaseConnection();
        } catch (Exception e) {
          LOG.infoAndDebugDetails("Got exception while trying to release connection for " + method, e);
        }
      }
    }, executorService);
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
