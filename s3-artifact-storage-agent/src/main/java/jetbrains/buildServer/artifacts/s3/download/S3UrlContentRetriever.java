package jetbrains.buildServer.artifacts.s3.download;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TCSystemInfo;
import java.io.*;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import jetbrains.buildServer.artifacts.FileProgress;
import jetbrains.buildServer.artifacts.ProgressTrackingURLContentRetriever;
import jetbrains.buildServer.artifacts.RecoverableIOException;
import jetbrains.buildServer.artifacts.URLContentRetriever;
import jetbrains.buildServer.artifacts.impl.DependencyHttpHelper;
import jetbrains.buildServer.artifacts.s3.download.strategy.*;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.HeadMethod;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.artifacts.s3.download.S3DownloadHttpUtil.*;

public class S3UrlContentRetriever implements URLContentRetriever, ProgressTrackingURLContentRetriever {
  private static final Logger LOGGER = Logger.getInstance(S3UrlContentRetriever.class);

  @NotNull private final S3HttpClient myHttpClient;
  @NotNull private final ExecutorService myExecutorService;
  @NotNull private final DependencyHttpHelper myDependencyHttpHelper;
  @NotNull private final S3DownloadConfiguration myDownloadConfiguration;
  @NotNull private final ConcurrentHashMap<UUID, HttpMethod> myPendingRequestsById = new ConcurrentHashMap<>();
  @NotNull private final AtomicBoolean myIsInterrupted = new AtomicBoolean(false);
  private final int myMaxRedirects;

  public S3UrlContentRetriever(@NotNull String serverUrl,
                               @NotNull HttpClient httpClient,
                               @NotNull ExecutorService executorService,
                               @NotNull DependencyHttpHelper dependencyHttpHelper,
                               @NotNull S3DownloadConfiguration downloadConfiguration) {
    myHttpClient = new S3HttpClient(httpClient, dependencyHttpHelper, serverUrl);
    myExecutorService = executorService;
    myDependencyHttpHelper = dependencyHttpHelper;
    myDownloadConfiguration = downloadConfiguration;
    myMaxRedirects = httpClient.getParams().getIntParameter(HttpClientParams.MAX_REDIRECTS, 10);
  }

  @Override
  @Nullable
  public String downloadUrlTo(@NotNull String srcUrl, @NotNull File target) throws IOException {
    return doDownload(srcUrl, target, new FileProgress.Adapter());
  }

  @Override
  @Nullable
  public String downloadUrlTo(@NotNull final String srcUrl, @NotNull final File target, @NotNull final FileProgress fileDownloadProgress) throws IOException {
    return doDownload(srcUrl, target, fileDownloadProgress);
  }

  @Nullable
  private String doDownload(@NotNull final String srcUrl, @NotNull final File target, @NotNull final FileProgress fileDownloadProgress) throws IOException {
    LOGGER.debug(String.format("Start downloading file %s from %s", target, srcUrl));
    try {
      checkIfInterrupted();
      FileSource directFileSource = seekDirectFileSourceFromLevel(srcUrl, 0);

      String directUrl = directFileSource.getUrl();
      long fileSize = directFileSource.getFileSize();
      LOGGER.debug(String.format("Found direct URL for downloading file %s: %s", target, directUrl));

      FileDownloadStrategy downloadStrategy = getFileDownloadStrategy(fileSize, fileDownloadProgress);
      if (downloadStrategy == null) {
        LOGGER.info(String.format("Skip downloading file %s: no applicable strategy found", target));
        return null; // this delegates the download to the next URLContentRetriever inside CompositeURLContentRetriever
      }
      LOGGER.debug(String.format("Found strategy \"%s\" for downloading file %s", downloadStrategy.getName(), target));

      checkIfInterrupted();
      downloadStrategy.download(directUrl, target.toPath(), fileSize); // throws exception when fails
      return directFileSource.getFileDigest();
    } catch (IOException | RuntimeException e) {
      LOGGER.warnAndDebugDetails(String.format("Failed to download file %s from %s", target, srcUrl), e);
      throw new IOException(String.format("Failed to download file %s from %s", target, srcUrl), e);
    }
  }

  @NotNull
  private FileSource seekDirectFileSourceFromLevel(@NotNull String redirectableUrl, int redirectLevel) throws IOException {
    GetMethod request = new GetMethod(redirectableUrl);
    UUID requestId = UUID.randomUUID();

    try {
      checkIfInterrupted();
      myPendingRequestsById.compute(requestId, (id, oldRequest) -> {
        if (oldRequest != null) {
          // we have a UUID collision (astronomically low likelihood)
          LOGGER.warn("Request UUID collision, releasing connection of the previous request");
          releaseConnectionIfNotNull(oldRequest);
        }
        return request;
      });

      checkIfInterrupted();
      int statusCode = myHttpClient.execute(request);

      if (isRedirectStatus(statusCode)) {
        checkIfInterrupted();
        if (redirectLevel >= myMaxRedirects) throw new RedirectException(String.format("Maximum number of redirects (%s) exceeded", myMaxRedirects));

        Header location = request.getResponseHeader("Location");
        if (location == null) throw new IOException(String.format("Status code %s requires redirect, but no \"Location\" header provided", statusCode));

        releaseConnectionIfNotNull(request);
        return seekDirectFileSourceFromLevel(getNormalizedLocation(request, location), redirectLevel + 1);
      }

      checkResponseStatus(statusCode, HttpStatus.SC_OK);
      checkAcceptsByteRanges(request);
      long fileSize = getResponseContentLength(request);
      String fileDigest = myDependencyHttpHelper.fetchDigest(request);

      // found direct url, abort request not to read response body
      // this closes the associated connection, but this is fine because it happens only once per downloaded file
      request.abort();
      return new FileSource(redirectableUrl, fileSize, fileDigest);
    } finally {
      releaseConnectionIfNotNull(request);
      myPendingRequestsById.remove(requestId);
    }
  }

  @NotNull
  private String getNormalizedLocation(@NotNull final GetMethod request, @NotNull final Header location) throws URIException {
    String locationValue = location.getValue();
    URI uri = new URI(locationValue, true); // uri is anyway constructed in new GetMethod(srcUrl) so no need to catch exception
    if (uri.getHost() == null) return request.getHostConfiguration().getHostURL() + (locationValue.startsWith("/") ? locationValue : "/" + locationValue);
    return locationValue;
  }

  public void releaseConnectionIfNotNull(@Nullable HttpMethod request) {
    if (request != null) request.releaseConnection();
  }

  @Nullable
  private FileDownloadStrategy getFileDownloadStrategy(long fileSize, @NotNull FileProgress downloadProgress) {
    if (fileSize < myDownloadConfiguration.getParallelDownloadFileSizeThreshold() || myDownloadConfiguration.getMaxThreads() == 1) return null; // skip sequential downloads
    FileDownloadStrategyType forcedStrategyType = myDownloadConfiguration.getForcedDownloadStrategyType();
    if (forcedStrategyType != null) {
      LOGGER.info(String.format("Download strategy is forced to %s", forcedStrategyType));
      switch (forcedStrategyType) {
        case INPLACE_PARALLEL:
          return new InplaceParallelDownloadStrategy(myHttpClient, myExecutorService, myDownloadConfiguration, myIsInterrupted, downloadProgress);
        case SEPARATE_PART_FILES_PARALLEL:
          return new SeparatePartFilesParallelDownloadStrategy(myHttpClient, myExecutorService, myDownloadConfiguration, myIsInterrupted, downloadProgress);
        default:
          throw new RuntimeException(String.format("Forced unknown download strategy %s", downloadProgress));
      }
    }

    return TCSystemInfo.isWindows // on Windows parallel writes to different sectors of one file perform poorly
           ? new SeparatePartFilesParallelDownloadStrategy(myHttpClient, myExecutorService, myDownloadConfiguration, myIsInterrupted, downloadProgress)
           : new InplaceParallelDownloadStrategy(myHttpClient, myExecutorService, myDownloadConfiguration, myIsInterrupted, downloadProgress);
  }

  @Override
  public String getId() {
    return "S3 artifact transport"; // not really the ID, it's a human-readable name that is displayed in the build log
  }

  @Override
  @Nullable
  public String getDigest(@NotNull String srcUrl) throws IOException {
    LOGGER.debug(String.format("Getting digest from %s", srcUrl));
    HttpMethod request = new HeadMethod(srcUrl);
    try {
      checkIfInterrupted();
      int statusCode = myHttpClient.execute(request);

      checkResponseStatus(statusCode, HttpStatus.SC_OK);
      String digest = myDependencyHttpHelper.fetchDigest(request);
      LOGGER.debug(String.format("Got digest from %s", srcUrl));
      return digest;
    } catch (IOException | RuntimeException e) {
      LOGGER.warnAndDebugDetails(String.format("Failed to get digest from %s", srcUrl), e);
      throw new IOException(String.format("Failed to get digest from %s", srcUrl), e);
    } finally {
      request.releaseConnection();
    }
  }

  private void checkIfInterrupted() throws RecoverableIOException {
    if (myIsInterrupted.get()) throw new RecoverableIOException("Download was interrupted");
  }

  @Override
  public void interrupt() {
    myIsInterrupted.set(true);
    Collection<HttpMethod> pendingRequests = myPendingRequestsById.values();
    pendingRequests.forEach(request -> request.abort());
    pendingRequests.clear();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName();
  }

  private final class FileSource {
    @NotNull
    private final String url;
    private final long fileSize;
    @NotNull
    private final String fileDigest;

    public FileSource(@NotNull String url, long fileSize, @NotNull String fileDigest) {
      this.url = url;
      this.fileSize = fileSize;
      this.fileDigest = fileDigest;
    }

    @NotNull
    public String getUrl() {
      return url;
    }

    public long getFileSize() {
      return fileSize;
    }

    @NotNull
    public String getFileDigest() {
      return fileDigest;
    }
  }
}
