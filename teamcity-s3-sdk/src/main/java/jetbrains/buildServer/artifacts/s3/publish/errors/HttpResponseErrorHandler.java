package jetbrains.buildServer.artifacts.s3.publish.errors;

import java.util.Arrays;
import java.util.HashSet;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.HttpClientUtil;
import org.jetbrains.annotations.NotNull;

public interface HttpResponseErrorHandler {
  @NotNull
  static final HashSet<Integer> OUR_RECOVERABLE_STATUS_CODES = new HashSet<>(Arrays.asList(0, 500, 502, 503, 504));

  public boolean canHandle(@NotNull ResponseAdapter responseWrapper);

  @NotNull
  public HttpClientUtil.HttpErrorCodeException handle(@NotNull ResponseAdapter responseWrapper);

  @NotNull
  public default String name() {
    return getClass().getName();
  }
}
