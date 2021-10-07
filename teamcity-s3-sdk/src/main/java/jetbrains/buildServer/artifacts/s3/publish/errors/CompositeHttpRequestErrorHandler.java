package jetbrains.buildServer.artifacts.s3.publish.errors;

import com.intellij.openapi.diagnostic.Logger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.HttpClientUtil;
import org.jetbrains.annotations.NotNull;

public class CompositeHttpRequestErrorHandler implements HttpResponseErrorHandler {
  @NotNull
  private static final Logger LOGGER = Logger.getInstance(CompositeHttpRequestErrorHandler.class);
  @NotNull
  private final List<HttpResponseErrorHandler> myHttpRequestErrorHandlers = new ArrayList<>();
  @NotNull
  private final HttpResponseErrorHandler myDefaultHandler;

  public CompositeHttpRequestErrorHandler(@NotNull final HttpResponseErrorHandler... errorHandlers) {
    Collections.addAll(myHttpRequestErrorHandlers, errorHandlers);
    myDefaultHandler = new HttpResponseErrorHandler() {
      @Override
      public boolean canHandle(@NotNull ResponseAdapter responseWrapper) {
        return true;
      }

      @NotNull
      @Override
      public HttpClientUtil.HttpErrorCodeException handle(@NotNull ResponseAdapter responseWrapper) {
        return new HttpClientUtil.HttpErrorCodeException(responseWrapper.getStatusCode(), responseWrapper.getResponse(), false);
      }

      @NotNull
      @Override
      public String name() {
        return "DefaultResponseErrorHandler";
      }
    };
  }

  @Override
  public boolean canHandle(@NotNull final ResponseAdapter responseWrapper) {
    return myHttpRequestErrorHandlers.stream().anyMatch(handler -> handler.canHandle(responseWrapper));
  }

  @NotNull
  @Override
  public HttpClientUtil.HttpErrorCodeException handle(@NotNull final ResponseAdapter responseWrapper) {
    final HttpResponseErrorHandler errorHandler = myHttpRequestErrorHandlers.stream()
                                                                            .findFirst()
                                                                            .orElse(myDefaultHandler);
    LOGGER.debug(() -> "Handling response error with '" + errorHandler.name() + "' started");
    final HttpClientUtil.HttpErrorCodeException handle = tryHandle(responseWrapper, errorHandler);
    LOGGER.debug(() -> "Handling response error with '" + errorHandler.name() + "' finished. Got " + (handle.isRecoverable() ? " recoverable " : " unrecoverable") + " problem");
    return handle;
  }

  @NotNull
  private HttpClientUtil.HttpErrorCodeException tryHandle(@NotNull ResponseAdapter responseWrapper, HttpResponseErrorHandler errorHandler) {
    try {
      return errorHandler.handle(responseWrapper);
    } catch (final Exception e) {
      if (errorHandler != myDefaultHandler) {
        LOGGER.infoAndDebugDetails(() -> "Handling response error with '" + errorHandler.name() + "' failed with exception, will try with '" + myDefaultHandler.name() + "'", e);
        try {
          return myDefaultHandler.handle(responseWrapper);
        } catch (final Exception exception) {
          LOGGER.infoAndDebugDetails(() -> "Handling response error with '" + myDefaultHandler.name() + "' failed with exception", e);
          throw e;
        }
      } else {
        LOGGER.infoAndDebugDetails(() -> "Handling response error with '" + errorHandler.name() + "' failed with exception", e);
        throw e;
      }
    }
  }
}
