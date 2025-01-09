package jetbrains.buildServer.artifacts.s3.download;

import com.intellij.openapi.diagnostic.Logger;
import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.artifacts.FileProgress;
import jetbrains.buildServer.artifacts.ProgressTrackingURLContentRetriever;
import jetbrains.buildServer.artifacts.RecoverableIOException;
import jetbrains.buildServer.artifacts.URLContentRetriever;
import jetbrains.buildServer.artifacts.impl.DependencyHttpHelper;
import jetbrains.buildServer.artifacts.s3.download.parallel.*;
import jetbrains.buildServer.artifacts.s3.download.parallel.splitter.FileSplitter;
import jetbrains.buildServer.artifacts.s3.download.parallel.splitter.SplitabilityReport;
import jetbrains.buildServer.artifacts.s3.download.parallel.splitter.impl.FileSplitterImpl;
import jetbrains.buildServer.artifacts.s3.download.parallel.strategy.ParallelDownloadStrategy;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.HeadMethod;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static java.nio.file.StandardOpenOption.*;
import static jetbrains.buildServer.artifacts.s3.S3Constants.*;
import static jetbrains.buildServer.artifacts.s3.download.S3DownloadIOUtil.*;
import static jetbrains.buildServer.artifacts.s3.download.S3DownloadHttpUtil.*;

public class S3ArtifactTransport implements URLContentRetriever, ProgressTrackingURLContentRetriever {
  private static final Logger LOGGER = Logger.getInstance(S3ArtifactTransport.class);

  @NotNull private final S3HttpClient myhttpClient;
  @NotNull private final ExecutorService myExecutorService;
  @NotNull private final DependencyHttpHelper myDependencyHttpHelper;
  @NotNull private final S3DownloadConfiguration myConfiguration;
  @NotNull private final AgentRunningBuild myRunningBuild;
  @NotNull private final Map<String, ParallelDownloadStrategy> myParallelDownloadStrategiesByName;
  @NotNull private final FileSplitter myFileSplitter;
  @NotNull private final ConcurrentHashMap<UUID, HttpMethod> myPendingRequestsById = new ConcurrentHashMap<>();
  @NotNull private final AtomicBoolean myIsInterrupted = new AtomicBoolean(false);
  private final int myMaxRedirects;

  public S3ArtifactTransport(@NotNull String serverUrl,
                             @NotNull HttpClient httpClient,
                             @NotNull ExecutorService executorService,
                             @NotNull DependencyHttpHelper dependencyHttpHelper,
                             @NotNull S3DownloadConfiguration configuration,
                             @NotNull AgentRunningBuild runningBuild,
                             @NotNull Map<String, ParallelDownloadStrategy> parallelDownloadStrategiesByName) {
    myhttpClient = new S3HttpClient(httpClient, dependencyHttpHelper, serverUrl);
    myExecutorService = executorService;
    myDependencyHttpHelper = dependencyHttpHelper;
    myConfiguration = configuration;
    myRunningBuild = runningBuild;
    myParallelDownloadStrategiesByName = parallelDownloadStrategiesByName;
    myFileSplitter = new FileSplitterImpl(configuration);
    myMaxRedirects = httpClient.getParams().getIntParameter(HttpClientParams.MAX_REDIRECTS, 10);
  }

  @Override
  @Nullable
  public String downloadUrlTo(@NotNull String srcUrl, @NotNull File targetFile) throws IOException {
    return doDownload(srcUrl, targetFile, new FileProgress.Adapter());
  }

  @Override
  @Nullable
  public String downloadUrlTo(@NotNull String srcUrl, @NotNull File targetFile, @NotNull FileProgress downloadProgress) throws IOException {
    return doDownload(srcUrl, targetFile, downloadProgress);
  }

  @NotNull
  private String doDownload(@NotNull String srcUrl, @NotNull File targetFile, @NotNull FileProgress downloadProgress) throws IOException {
    LOGGER.debug(String.format("Start downloading file %s from %s", targetFile, srcUrl));
    try {
      checkIfInterrupted();
      Path targetFilePath = getAbsoluteNormalizedPath(targetFile.toPath());
      LOGGER.debug(String.format("File path was normalized from %s to %s", targetFile, targetFilePath));
      RedirectFollowingResult result = followRedirects(srcUrl, targetFilePath, downloadProgress, 0);
      if (result.isShouldDownloadInParallel()) {
        checkIfInterrupted();
        ParallelDownloadStrategy parallelStrategy = getParallelStrategy();
        LOGGER.debug(String.format("File %s will be downloaded in parallel using startegy %s", targetFilePath, parallelStrategy.getName()));
        Long contentLength = result.getContentLength();
        Objects.requireNonNull(contentLength, "Content length must not be null");
        ParallelDownloadContext parallelDownloadContext = new ParallelDownloadContext(myConfiguration, myRunningBuild, myFileSplitter, myhttpClient, myExecutorService, myIsInterrupted);
        parallelStrategy.download(result.getDirectUrl(), targetFilePath, contentLength, downloadProgress, parallelDownloadContext);
      }

      LOGGER.debug(String.format("Finished downloading file %s from %s", targetFile, srcUrl));
      return result.getDigest();
    } catch (IOException | RuntimeException e) {
      LOGGER.warn(String.format("Failed to download file %s from %s: %s", targetFile, srcUrl, e.getMessage()), e);
      throw new IOException(String.format("Failed to download file %s from %s", targetFile, srcUrl), e);
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
      int statusCode = myhttpClient.execute(request);

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

      Long contentLength = getContentLength(request);
      String fileDigest = myDependencyHttpHelper.fetchDigest(request);
      if (isParallelisationPossible(srcUrl, targetFile, contentLength, canAcceptByteRanges(request))) {
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
    if (contentLength == null || contentLength <= 0) {
      LOGGER.debug(String.format("File %s will not be downloaded in parallel: content length is %s", targetFile, contentLength));
      return false;
    }

    if (!acceptsRanges) {
      LOGGER.debug(String.format("File %s will not be downloaded in parallel: direct URL %s doesn't accept byte ranges", targetFile, directUrl));
      return false;
    }

    if (myConfiguration.getMaxThreads() == 1) {
      LOGGER.debug(String.format("File %s will not be downloaded in parallel: max parallelism is 1 (can be changed by %s configuration parameter)",
                                 targetFile, S3_PARALLEL_DOWNLOAD_MAX_THREADS));
      return false;
    }

    long maxFileSize = myConfiguration.getMaxFileSizeBytes();
    if (contentLength >= maxFileSize) {
      LOGGER.debug(String.format("File %s will not be downloaded in parallel: file size %s is greater than threshold %s (can be changed by %s configuration parameter)",
                                 targetFile, contentLength, maxFileSize, S3_PARALLEL_DOWNLOAD_MAX_FILE_SIZE_GB));
      return false;
    }

    SplitabilityReport splitabilityReport = myFileSplitter.testSplitability(contentLength);
    if (!splitabilityReport.isSplittable()) {
      LOGGER.debug(String.format("File %s will not be downloaded in parallel: it cannot be split into parts: %s", targetFile, splitabilityReport.getUnsplitablilityReason()));
      return false;
    }

    LOGGER.debug(String.format("File %s can be downloaded in parallel", targetFile));
    return true;
  }

  public void downloadSequentially(@NotNull HttpMethod ongoingRequest,
                                   @NotNull Path targetFile,
                                   @Nullable Long fileSize,
                                   @NotNull FileProgress downloadProgress) throws IOException {
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
         WritableByteChannel targetFileChannel = Files.newByteChannel(targetFile, CREATE, WRITE, TRUNCATE_EXISTING)) {
      if (fileSize != null && fileSize >= 0) {
        transferExpectedBytes(
          responseBodyChannel,
          targetFileChannel,
          fileSize,
          myConfiguration.getBufferSizeBytes(),
          () -> checkIfInterrupted(),
          (transferred) -> downloadProgress.transferred(transferred)
        );
      } else {
        // some files are served witout the Content-Length header, download them without the size check
        transferAllBytes(
          responseBodyChannel,
          targetFileChannel,
          myConfiguration.getBufferSizeBytes(),
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
  private ParallelDownloadStrategy getParallelStrategy() {
    String configuredStrategyName = myConfiguration.getParallelStrategyName();
    return Optional.ofNullable(myParallelDownloadStrategiesByName.get(configuredStrategyName))
      .orElseThrow(() -> new RuntimeException(
        String.format(
          "Parallel download strategy %s not found, available strategies: %s",
          configuredStrategyName, myParallelDownloadStrategiesByName.keySet())
      ));
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
      int statusCode = myhttpClient.execute(request);

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
