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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jetbrains.buildServer.artifacts.s3.publish.errors.HttpResponseAdapter;
import jetbrains.buildServer.artifacts.s3.publish.errors.HttpResponseErrorHandler;
import jetbrains.buildServer.util.HTTPRequestBuilder;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.amazon.retry.RecoverableException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitrii Bogdanov
 */
public final class HttpClientUtil {
  private static final ExecutorService myExecutorService = Executors.newCachedThreadPool();


  private HttpClientUtil() {
  }


  public static CompletableFuture<HttpResponseAdapter> executeAndReleaseConnection(@NotNull final HTTPRequestBuilder requestBuilder,
                                                                                   @NotNull final HttpResponseErrorHandler errorHandler) {
    return executeAndReleaseConnection(requestBuilder, errorHandler, myExecutorService);
  }

  public static CompletableFuture<HttpResponseAdapter> executeAndReleaseConnection(@NotNull final HTTPRequestBuilder requestBuilder,
                                                                                   @NotNull final HttpResponseErrorHandler errorHandler, ExecutorService executorService) {
    final CompletableFuture<HttpResponseAdapter> responseCompletableFuture = new CompletableFuture<>();
    CompletableFuture.runAsync(() -> {
      try {
        final HTTPRequestBuilder.Request request = requestBuilder
          .onException(e -> responseCompletableFuture.completeExceptionally(e))
          .onErrorResponse(response -> responseCompletableFuture.completeExceptionally(errorHandler.handle(new HttpResponseAdapter(response))))
          .onSuccess(response -> responseCompletableFuture.complete(new HttpResponseAdapter(response)))
          .build();
        new HTTPRequestBuilder.DelegatingRequestHandler().doRequest(request);
      } catch (Exception e) {
        responseCompletableFuture.completeExceptionally(e);
      }
    }, executorService);

    return responseCompletableFuture;
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
