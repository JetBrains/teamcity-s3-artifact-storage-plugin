package jetbrains.buildServer.artifacts.s3.publish.errors;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.util.impl.Lazy;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HttpMethodResponseAdapter implements ResponseAdapter {
  @NotNull
  private final HttpMethod myDelegate;
  @NotNull
  private final Lazy<Map<String, String>> myHeaders;
  @NotNull
  private final Lazy<String> myResponse;

  public HttpMethodResponseAdapter(@NotNull final HttpMethod httpMethod) {
    myDelegate = httpMethod;
    myHeaders = new Lazy<Map<String, String>>() {
      @NotNull
      @Override
      protected Map<String, String> createValue() {
        final Map<String, String> headers = new HashMap<>();
        for (Header responseHeader : myDelegate.getResponseHeaders()) {
          headers.merge(responseHeader.getName(), responseHeader.getValue(), String::concat);
        }
        return headers;
      }
    };
    myResponse = new Lazy<String>() {
      @Nullable
      @Override
      protected String createValue() {
        try {
          return httpMethod.getResponseBodyAsString();
        } catch (IOException e) {
          return "Couldn't read response '" + e.getMessage() + "'";
        }
      }
    };
  }

  @Override
  public int getStatusCode() {
    return myDelegate.getStatusCode();
  }

  @Nullable
  @Override
  public String getResponse() {
    return myResponse.get();
  }

  @NotNull
  @Override
  public Map<String, String> getHeaders() {
    return myHeaders.get();
  }

  @Nullable
  @Override
  public String getHeader(@NotNull final String header) {
    final Header responseHeader = myDelegate.getResponseHeader(header);
    if (responseHeader == null) {
      return null;
    } else {
      return responseHeader.getValue();
    }
  }
}
