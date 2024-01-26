package jetbrains.buildServer.artifacts.s3.lens.integration;

import jetbrains.buildServer.artifacts.s3.publish.errors.HttpResponseErrorHandler;
import jetbrains.buildServer.artifacts.s3.publish.errors.ResponseAdapter;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.HttpClientUtil;
import org.jetbrains.annotations.NotNull;

class LensResponseErrorHandler implements HttpResponseErrorHandler {
  @Override
  public boolean canHandle(@NotNull ResponseAdapter responseWrapper) {
    // handle every error
    return true;
  }

  @NotNull
  @Override
  public HttpClientUtil.HttpErrorCodeException handle(@NotNull ResponseAdapter responseWrapper) {
    // just wrap it into HttpClientUtil.HttpErrorCodeException and return
    return new HttpClientUtil.HttpErrorCodeException(responseWrapper.getStatusCode(), responseWrapper.getResponse(), true);
  }
}
