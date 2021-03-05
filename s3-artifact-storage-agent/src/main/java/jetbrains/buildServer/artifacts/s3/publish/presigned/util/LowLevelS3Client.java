package jetbrains.buildServer.artifacts.s3.publish.presigned.util;

import java.io.*;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.http.HttpUserAgent;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.StringUtil;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.FileRequestEntity;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpResponseException;
import org.jetbrains.annotations.NotNull;

public class LowLevelS3Client implements AutoCloseable {
  private static final int OUR_CHUNK_SIZE = TeamCityProperties.getInteger(S3Constants.S3_PRESIGNED_UPLOAD_INTERNAL_CHUNK_SIZE, 64 * 1024);
  @NotNull
  private final HttpClient myHttpClient;

  public LowLevelS3Client(@NotNull final HttpClient httpClient) {
    myHttpClient = httpClient;
  }

  @Override
  public void close() {
    HttpClientUtil.shutdown(myHttpClient);
  }

  public void uploadFile(@NotNull final String url, @NotNull final File file) throws IOException {
    final PutMethod putMethod = new PutMethod(url);
    putMethod.setRequestHeader(HttpHeaders.USER_AGENT, HttpUserAgent.getUserAgent());
    putMethod.setRequestEntity(new FileRequestEntity(file, S3Util.getContentType(file)));
    HttpClientUtil.executeAndReleaseConnection(myHttpClient, putMethod);
  }

  @NotNull
  public String uploadFilePart(@NotNull final String url, @NotNull final File file, final long start, final long size) throws IOException {
    final PutMethod request = new PutMethod(url);
    request.setRequestHeader(HttpHeaders.USER_AGENT, HttpUserAgent.getUserAgent());

    request.setRequestEntity(new RequestEntity() {
      @Override
      public boolean isRepeatable() {
        return true;
      }

      @Override
      public void writeRequest(@NotNull final OutputStream out) throws IOException {
        long remaining = size;
        final byte[] buffer = new byte[(int)Math.min(OUR_CHUNK_SIZE, remaining)];
        try (final FileInputStream fis = new FileInputStream(file);
             final BufferedInputStream bis = new BufferedInputStream(fis)) {
          do {
            final int currentChunkSize = (int)Math.min(buffer.length, remaining);
            final int read = bis.read(buffer, 0, currentChunkSize);
            if (read != currentChunkSize) {
              throw new IOException("Reader has read " + read + " bytes when supposed to read " + buffer.length);
            }
              remaining -= currentChunkSize;
            out.write(buffer, 0, currentChunkSize);
          } while (remaining > 0);
        }
      }

      @Override
      public long getContentLength() {
        return size;
      }

      @Override
      public String getContentType() {
        return S3Util.getContentType(file);
      }
    });
    HttpClientUtil.executeAndReleaseConnection(myHttpClient, request);
    final Header eTags = request.getResponseHeader("ETag");
    if (eTags != null) {
      return StringUtil.unquoteString(eTags.getValue());
    } else {
      throw new HttpResponseException(HttpStatus.SC_BAD_REQUEST, "Unexpected response from S3: doesn't contain etags headers");
    }
  }
}
