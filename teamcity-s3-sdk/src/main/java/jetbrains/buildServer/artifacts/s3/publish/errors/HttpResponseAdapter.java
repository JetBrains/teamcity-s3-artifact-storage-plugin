package jetbrains.buildServer.artifacts.s3.publish.errors;

import java.io.IOException;
import jetbrains.buildServer.util.HTTPRequestBuilder;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HttpResponseAdapter implements ResponseAdapter {
  @NotNull
  private final HTTPRequestBuilder.Response myDelegate;
  @Nullable
  private final String myResponse;

  public HttpResponseAdapter(@NotNull final HTTPRequestBuilder.Response response) throws IOException {
    myDelegate = response;
    myResponse = response.getBodyAsString();
  }

  @Override
  public int getStatusCode() {
    return myDelegate.getStatusCode();
  }

  @Nullable
  @Override
  public String getResponse() {
    return myResponse;
  }

  @Nullable
  @Override
  public String getHeader(@NotNull final String header) {
    final String responseHeader = myDelegate.getHeader(header);
    if (StringUtil.isEmpty(responseHeader)) {
      return null;
    } else {
      return responseHeader;
    }
  }
}
