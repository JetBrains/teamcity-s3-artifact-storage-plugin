

package jetbrains.buildServer.artifacts.s3.publish.presigned.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import jetbrains.buildServer.artifacts.s3.publish.errors.HttpResponseAdapter;
import jetbrains.buildServer.artifacts.s3.publish.errors.HttpResponseErrorHandler;
import jetbrains.buildServer.util.HTTPRequestBuilder;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.executors.ExecutorsFactory;
import jetbrains.buildServer.util.impl.Lazy;
import jetbrains.buildServer.util.retry.RecoverableException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitrii Bogdanov
 */
public final class HttpClientUtil {
  private static final Lazy<ExecutorService> myDefaultExecutorService = new Lazy<ExecutorService>() {
    @NotNull
    @Override
    protected ExecutorService createValue() {
      return ExecutorsFactory.newDaemonExecutor("S3HttpClientUtil");
    }
  };


  private HttpClientUtil() {
  }


  public static CompletableFuture<HttpResponseAdapter> executeAndReleaseConnection(@NotNull final HTTPRequestBuilder requestBuilder,
                                                                                   @NotNull final HttpResponseErrorHandler errorHandler) {
    return executeAndReleaseConnection(requestBuilder, errorHandler, myDefaultExecutorService.get());
  }

  public static CompletableFuture<HttpResponseAdapter> executeAndReleaseConnection(@NotNull final HTTPRequestBuilder requestBuilder,
                                                                                   @NotNull final HttpResponseErrorHandler errorHandler, ExecutorService executorService) {
    final CompletableFuture<HttpResponseAdapter> responseCompletableFuture = new CompletableFuture<>();
    CompletableFuture.runAsync(() -> {
                       final HTTPRequestBuilder.Request request = requestBuilder
                         .onException(e -> responseCompletableFuture.completeExceptionally(e))
                         .onErrorResponse(response -> responseCompletableFuture.completeExceptionally(errorHandler.handle(new HttpResponseAdapter(response))))
                         .onSuccess(response -> responseCompletableFuture.complete(new HttpResponseAdapter(response)))
                         .build();
                       new HTTPRequestBuilder.DelegatingRequestHandler().doRequest(request);
                     }, executorService)
                     .exceptionally(e -> {
                       responseCompletableFuture.completeExceptionally(e);
                       return null;
                     });

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
