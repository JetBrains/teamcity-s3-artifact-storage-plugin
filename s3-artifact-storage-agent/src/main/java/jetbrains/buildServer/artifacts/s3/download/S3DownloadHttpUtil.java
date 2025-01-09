package jetbrains.buildServer.artifacts.s3.download;

import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.util.Optional;
import jetbrains.buildServer.artifacts.RecoverableIOException;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.http.HttpHeaders;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class S3DownloadHttpUtil {
  public static final Logger LOGGER = Logger.getInstance(S3DownloadHttpUtil.class);

  @Nullable
  public static Long getContentLength(@NotNull GetMethod request) {
    try {
      return Optional.ofNullable(request.getResponseHeader(HttpHeaders.CONTENT_LENGTH))
                     .map(Header::getValue)
                     .map(Long::parseLong)
                     .orElse(null);
    } catch (RuntimeException e) {
      LOGGER.debug(String.format("Failed to parse the %s response header", HttpHeaders.CONTENT_LENGTH), e);
      return null;
    }
  }

  public static boolean canAcceptByteRanges(@NotNull HttpMethod request) {
    return Optional.ofNullable(request.getResponseHeader(HttpHeaders.ACCEPT_RANGES))
                   .map(Header::getValue)
                   .map(value -> "bytes".equalsIgnoreCase(value))
                   .orElse(false);
  }

  public static boolean isRedirectStatus(int statusCode) {
    switch (statusCode) {
      case HttpStatus.SC_MOVED_PERMANENTLY:
      case HttpStatus.SC_MOVED_TEMPORARILY:
      case HttpStatus.SC_SEE_OTHER:
      case HttpStatus.SC_TEMPORARY_REDIRECT:
        return true;
      default:
        return false;
    }
  }

  public static boolean isRecoverableStatus(int statusCode) {
    switch (statusCode) {
      // retry 5xx errors just in case some proxy is faulty or server is in cleanup (503)
      case HttpStatus.SC_INTERNAL_SERVER_ERROR:
      case HttpStatus.SC_NOT_IMPLEMENTED:
      case HttpStatus.SC_BAD_GATEWAY:
      case HttpStatus.SC_SERVICE_UNAVAILABLE:
      case HttpStatus.SC_GATEWAY_TIMEOUT:
        return true;

      // retry timeouted requests
      case HttpStatus.SC_REQUEST_TIMEOUT:
        return true;

      // when the artifact storage plugin gets reloaded, there is a short amount of time when the old plugin is already
      // unloaded but the new one is not yet loaded; this results in controller not being registered anymore, thus returning 404
      case HttpStatus.SC_NOT_FOUND:
        return true;
      default:
        return false;
    }
  }

  public static void checkResponseStatus(int statusCode, int expectedStatusCode) throws IOException {
    if (statusCode == expectedStatusCode) return;

    String message = String.format("Expected status %s, received %s", expectedStatusCode, statusCode);
    if (isRecoverableStatus(statusCode)) {
      throw new RecoverableIOException(message);
    } else {
      throw new IOException(message);
    }
  }

  @NotNull
  public static Header createRangeHeader(long startByte, long endByte) {
    return new Header("Range", "bytes=" + startByte + "-" + endByte);
  }
}
