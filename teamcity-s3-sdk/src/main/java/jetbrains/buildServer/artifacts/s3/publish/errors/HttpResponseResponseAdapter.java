package jetbrains.buildServer.artifacts.s3.publish.errors;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.util.impl.Lazy;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HttpResponseResponseAdapter implements ResponseAdapter {
  @NotNull
  private final HttpResponse myDelegate;
  @NotNull
  private final Lazy<Map<String, String>> myHeaders;
  @NotNull
  private final Lazy<String> myResponse;

  public HttpResponseResponseAdapter(@NotNull final HttpResponse httpPost) {
    myDelegate = httpPost;
    myHeaders = new Lazy<Map<String, String>>() {
      @NotNull
      @Override
      protected Map<String, String> createValue() {
        final Map<String, String> headers = new HashMap<>();
        for (Header responseHeader : myDelegate.getAllHeaders()) {
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
          return EntityUtils.toString(myDelegate.getEntity());
        } catch (IOException e) {
          return "Couldn't read response '" + e.getMessage() + "'";
        }
      }
    };
  }

  @Override
  public int getStatusCode() {
    return myDelegate.getStatusLine().getStatusCode();
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
    final Header responseHeaders = myDelegate.getFirstHeader(header);
    if (responseHeaders == null) {
      return null;
    } else {
      return responseHeaders.getValue();
    }
  }
}
