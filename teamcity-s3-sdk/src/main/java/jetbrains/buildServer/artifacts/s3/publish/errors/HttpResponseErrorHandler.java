package jetbrains.buildServer.artifacts.s3.publish.errors;

import jetbrains.buildServer.artifacts.s3.publish.presigned.util.HttpClientUtil;
import org.jetbrains.annotations.NotNull;

public interface HttpResponseErrorHandler {
  public boolean canHandle(@NotNull ResponseAdapter responseWrapper);

  @NotNull
  public HttpClientUtil.HttpErrorCodeException handle(@NotNull ResponseAdapter responseWrapper);

  @NotNull
  public default String name() {
    return getClass().getName();
  }
}
