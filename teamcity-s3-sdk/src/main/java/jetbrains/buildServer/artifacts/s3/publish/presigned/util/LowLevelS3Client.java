package jetbrains.buildServer.artifacts.s3.publish.presigned.util;

import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.artifacts.s3.cloudfront.CloudFrontConstants;
import jetbrains.buildServer.artifacts.s3.exceptions.FileUploadFailedException;
import jetbrains.buildServer.artifacts.s3.publish.errors.*;
import jetbrains.buildServer.http.HttpUserAgent;
import jetbrains.buildServer.http.HttpUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.amazon.S3Util.S3AdvancedConfiguration;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.methods.EntityEnclosingMethod;
import org.apache.commons.httpclient.methods.FileRequestEntity;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.http.HttpHeaders;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LowLevelS3Client implements AutoCloseable {
  @NotNull
  //Ensures that bucket owner has access to any objects we upload
  public static final Supplier<Map<String, String>> MANDATORY_HEADERS_FOR_SINGLE_UPLOAD = () -> {
    return CloudFrontConstants.isEnabled() ? Collections.singletonMap("x-amz-acl", "bucket-owner-full-control") : Collections.emptyMap();
  };
  @NotNull
  private static final Logger LOGGER = Logger.getInstance(LowLevelS3Client.class);
  @NotNull
  private final HttpClient myHttpClient;
  @NotNull
  private final HttpResponseErrorHandler myErrorHandler =
    new CompositeHttpRequestErrorHandler(new S3DirectResponseErrorHandler(), new S3ServerResponseErrorHandler(), new TeamCityPresignedUrlsProviderErrorHandler());
  private final boolean myCheckConsistency;

  public LowLevelS3Client(@NotNull final S3AdvancedConfiguration s3Config) {
    myHttpClient = HttpUtil.createHttpClient(s3Config.getConnectionTimeout());
    myHttpClient.setHttpConnectionManager(HttpClientUtil.createConnectionManager(s3Config.getConnectionTimeout(), s3Config.getNThreads()));
    myCheckConsistency = s3Config.isConsistencyCheckEnabled();
  }

  @NotNull
  public String uploadFile(@NotNull final String url, @NotNull final File file, @Nullable String digest) throws IOException {
    EntityEnclosingMethod request = put(url, new FileRequestEntity(file, S3Util.getContentType(file)), digest, MANDATORY_HEADERS_FOR_SINGLE_UPLOAD.get());
    return parseEtags(request);
  }

  @NotNull
  public String uploadFilePart(@NotNull final String url, @NotNull final FilePart filePart, @Nullable final String digest) throws IOException {
    final EntityEnclosingMethod request = put(url, new RepeatableFilePartRequestEntity(filePart), digest, Collections.emptyMap());
    return parseEtags(request);
  }

  @NotNull
  private String parseEtags(@NotNull final HttpMethodBase request) {
    final Header eTags = request.getResponseHeader("ETag");
    if (eTags != null) {
      return StringUtil.unquoteString(eTags.getValue());
    } else {
      throw new FileUploadFailedException("Response does not contain etags", true);
    }
  }

  @NotNull
  private EntityEnclosingMethod put(@NotNull final String url,
                                    @NotNull final RequestEntity requestEntity,
                                    @Nullable String digest,
                                    @NotNull final Map<String, String> additionalHeaders)
    throws IOException {
    final EntityEnclosingMethod request = putRequest(url);
    request.setRequestEntity(requestEntity);
    request.setRequestHeader("Accept", "application/xml");
    additionalHeaders.forEach((name, value) -> request.setRequestHeader(name, value));
    if (myCheckConsistency && digest != null) {
      request.setRequestHeader("Content-MD5", digest);
    }
    HttpClientUtil.executeAndReleaseConnection(myHttpClient, request, myErrorHandler);
    return request;
  }

  @NotNull
  private EntityEnclosingMethod putRequest(@NotNull String url) {
    return withUserAgent(new PutMethod(url));
  }

  @NotNull
  private <T extends HttpMethodBase> T withUserAgent(@NotNull final T request) {
    request.setRequestHeader(HttpHeaders.USER_AGENT, HttpUserAgent.getUserAgent());
    return request;
  }

  @Override
  public void close() {
    HttpClientUtil.shutdown(myHttpClient);
  }
}
