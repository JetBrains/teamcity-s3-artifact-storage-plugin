package jetbrains.buildServer.artifacts.s3.download;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TCSystemInfo;
import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;
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

import static jetbrains.buildServer.artifacts.s3.download.S3DownloadFileUtil.*;
import static jetbrains.buildServer.artifacts.s3.download.S3DownloadHttpUtil.*;

public class S3ArtifactTransport implements URLContentRetriever, ProgressTrackingURLContentRetriever {
  private static final Logger LOGGER = Logger.getInstance(S3ArtifactTransport.class);

  @NotNull private final S3HttpClient myHttpClient;
  @NotNull private final ExecutorService myExecutorService;
  @NotNull private final DependencyHttpHelper myDependencyHttpHelper;
  @NotNull private final S3DownloadConfiguration myDownloadConfiguration;
  @NotNull private final ConcurrentHashMap<UUID, HttpMethod> myPendingRequestsById = new ConcurrentHashMap<>();
  @NotNull private final AtomicBoolean myIsInterrupted = new AtomicBoolean(false);
  private final int myMaxRedirects;

  public S3ArtifactTransport(@NotNull String serverUrl,
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
  public String downloadUrlTo(@NotNull String srcUrl, @NotNull File target, @NotNull FileProgress fileDownloadProgress) throws IOException {
    return doDownload(srcUrl, target, fileDownloadProgress);
  }

  @NotNull
  private String doDownload(@NotNull String srcUrl, @NotNull File target, @NotNull FileProgress downloadProgress) throws IOException {
    LOGGER.debug(String.format("Start downloading file %s from %s", target, srcUrl));
    try {
      checkIfInterrupted();
      Path targetFile = target.toPath();
      RedirectFollowingResult result = followRedirects(srcUrl, targetFile, downloadProgress, 0);
      if (result.isShouldDownloadInParallel()) {
        checkIfInterrupted();
        FileDownloadStrategy parallelStrategy = selectParallelStrategy(downloadProgress);
        LOGGER.debug(String.format("Using strategy %s for downloading file %s", parallelStrategy.getName(), target));
        Long contentLength = result.getContentLength();
        Objects.requireNonNull(contentLength, "Content length must not be null");
        parallelStrategy.download(result.getDirectUrl(), targetFile, contentLength);
      }

      LOGGER.debug(String.format("Finished downloading file %s from %s", targetFile, srcUrl));
      return result.getDigest();
    } catch (IOException | RuntimeException e) {
      LOGGER.warn(String.format("Failed to download file %s from %s: %s", target, srcUrl, e.getMessage()), e);
      throw new IOException(String.format("Failed to download file %s from %s", target, srcUrl), e);
    }
  }

  @NotNull
  private RedirectFollowingResult followRedirects(@NotNull String srcUrl, @NotNull Path targetFile, @NotNull FileProgress downloadProgress, int redirectLevel) throws IOException {
    GetMethod request = new GetMethod(srcUrl);
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
        return followRedirects(getNormalizedLocation(request, location), targetFile, downloadProgress, redirectLevel + 1);
      }

      checkResponseStatus(statusCode, HttpStatus.SC_OK);
      LOGGER.debug(String.format("Found direct URL for downloading file %s: %s", targetFile, srcUrl));

      Long contentLength = getResponseContentLength(request);
      String fileDigest = myDependencyHttpHelper.fetchDigest(request);
      if (isParallelisationPossible(srcUrl, targetFile, contentLength, getAcceptsByteRanges(request))) {
        // abort request not to wait until full response body arrives
        // this closes the associated connection, but this is fine because it happens only once per large file that will be downloaded in parallel
        request.abort();
        return new RedirectFollowingResult(true, srcUrl, contentLength, fileDigest);
      }

      // perform sequential download right away
      LOGGER.debug(String.format("Start downloading file %s from %s sequentially", targetFile, srcUrl));
      downloadSequentially(request, targetFile, contentLength, downloadProgress);
      return new RedirectFollowingResult(false, srcUrl, contentLength, fileDigest);
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

  private boolean isParallelisationPossible(@NotNull String directUrl, @NotNull Path targetFile, @Nullable Long contentLength, boolean acceptsRanges) {
    if (myDownloadConfiguration.getMaxThreads() == 1) {
      LOGGER.debug(String.format("File %s will not be downloaded in parallel: max parallelism is 1", targetFile));
      return false;
    }

    if (contentLength == null || contentLength <= 0) {
      LOGGER.debug(String.format("File %s will not be downloaded in parallel: content length is %s", targetFile, contentLength));
      return false;
    }

    long minFileSize = myDownloadConfiguration.getParallelDownloadFileSizeThreshold();
    if (contentLength < minFileSize) {
      LOGGER.debug(String.format("File %s will not be downloaded in parallel: file size %s is less than threshold %s", targetFile, contentLength, minFileSize));
      return false;
    }

    long maxFileSize = myDownloadConfiguration.getMaxFileSizeBytes();
    if (contentLength >= maxFileSize) {
      LOGGER.debug(String.format("File %s will not be downloaded in parallel: file size %s is greater than threshold %s", targetFile, contentLength, maxFileSize));
      return false;
    }

    if (!acceptsRanges) {
      LOGGER.debug(String.format("File %s will not be downloaded in parallel: direct URL %s doesn't accept byte ranges", targetFile, directUrl));
      return false;
    }

    LOGGER.debug(String.format("File %s can be downloaded in parallel", targetFile));
    return true;
  }

  public void downloadSequentially(@NotNull HttpMethod ongoingRequest, @NotNull Path targetFile, @Nullable Long fileSize, @NotNull FileProgress downloadProgress)
    throws IOException {
    try {
      checkIfInterrupted();
      ensureDirectoryExists(targetFile.getParent());
      if (fileSize != null && fileSize >= 0) {
        downloadProgress.setExpectedLength(fileSize);
      }

      writeFile(ongoingRequest, targetFile, fileSize, downloadProgress);
    } catch (IOException | RuntimeException e) {
      cleanupUnfinishedDownload(targetFile);
      throw e;
    }
  }

  private void writeFile(@NotNull HttpMethod ongoingRequest, @NotNull Path targetFile, @Nullable Long fileSize, @NotNull FileProgress downloadProgress) throws IOException {
    checkIfInterrupted();
    try (ReadableByteChannel responseBodyChannel = Channels.newChannel(ongoingRequest.getResponseBodyAsStream());
         WritableByteChannel targetFileChannel = Files.newByteChannel(targetFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
      if (fileSize != null && fileSize >= 0) {
        transferExpectedBytes(
          responseBodyChannel,
          targetFileChannel,
          fileSize,
          myDownloadConfiguration.getBufferSizeBytes(),
          () -> checkIfInterrupted(),
          (transferred) -> downloadProgress.transferred(transferred)
        );
      } else {
        // some files are served witout the Content-Length header, download them without the size check
        transferAllBytes(
          responseBodyChannel,
          targetFileChannel,
          myDownloadConfiguration.getBufferSizeBytes(),
          () -> checkIfInterrupted(),
          (transferred) -> downloadProgress.transferred(transferred)
        );
      }
    } catch (IOException | RuntimeException e) {
      // aborting the request allows not to wait until full body arrives, this needs to be done before closing the response body stream
      ongoingRequest.abort();
      throw new IOException(String.format("Failed to write file %s", targetFile), e);
    }
  }

  private void cleanupUnfinishedDownload(@NotNull Path targetFile) {
    try {
      checkIfInterrupted();
      Files.deleteIfExists(targetFile);
    } catch (IOException e) {
      LOGGER.warnAndDebugDetails(String.format("Failed to cleanup unfinished download of file %s: %s", targetFile, e.getMessage()), e);
    }
  }

  public void releaseConnectionIfNotNull(@Nullable HttpMethod request) {
    if (request != null) request.releaseConnection();
  }

  @NotNull
  private ParallelDownloadStrategy selectParallelStrategy(@NotNull FileProgress downloadProgress) {
    FileDownloadStrategyType forcedStrategyType = myDownloadConfiguration.getForcedDownloadStrategyType();
    if (forcedStrategyType != null) {
      LOGGER.debug(String.format("Parallel download strategy is forced to %s", forcedStrategyType));
      switch (forcedStrategyType) {
        case INPLACE_PARALLEL:
          return new InplaceParallelDownloadStrategy(myHttpClient, myExecutorService, myDownloadConfiguration, myIsInterrupted, downloadProgress);
        case SEPARATE_PART_FILES_PARALLEL:
          return new SeparatePartFilesParallelDownloadStrategy(myHttpClient, myExecutorService, myDownloadConfiguration, myIsInterrupted, downloadProgress);
        default:
          throw new RuntimeException(String.format("Forced unknown parallel download strategy %s", forcedStrategyType));
      }
    }

    return new InplaceParallelDownloadStrategy(myHttpClient, myExecutorService, myDownloadConfiguration, myIsInterrupted, downloadProgress);
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
      LOGGER.warn(String.format("Failed to get digest from %s: %s", srcUrl, e.getMessage()), e);
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

  private final class RedirectFollowingResult {
    boolean shouldDownloadInParallel;
    @NotNull
    private final String directUrl;
    @Nullable
    private final Long contentLength;
    @NotNull
    private final String digest;

    public RedirectFollowingResult(boolean shouldDownloadInParallel, @NotNull String directUrl, @Nullable Long contentLength, @NotNull String digest) {
      this.shouldDownloadInParallel = shouldDownloadInParallel;
      this.directUrl = directUrl;
      this.contentLength = contentLength;
      this.digest = digest;
    }

    public boolean isShouldDownloadInParallel() {
      return shouldDownloadInParallel;
    }

    @NotNull
    public String getDirectUrl() {
      return directUrl;
    }

    @Nullable
    public Long getContentLength() {
      return contentLength;
    }

    @NotNull
    public String getDigest() {
      return digest;
    }
  }
}
