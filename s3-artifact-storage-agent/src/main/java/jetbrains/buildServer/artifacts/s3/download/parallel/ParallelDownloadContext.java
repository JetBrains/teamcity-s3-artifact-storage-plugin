package jetbrains.buildServer.artifacts.s3.download.parallel;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.artifacts.s3.download.S3DownloadConfiguration;
import jetbrains.buildServer.artifacts.s3.download.S3HttpClient;
import jetbrains.buildServer.artifacts.s3.download.parallel.splitter.FileSplitter;
import org.jetbrains.annotations.NotNull;

public final class ParallelDownloadContext {
  @NotNull
  private final S3DownloadConfiguration myConfiguration;
  @NotNull
  private final AgentRunningBuild myRunningBuild;
  @NotNull
  private final FileSplitter myFileSplitter;
  @NotNull
  private final S3HttpClient myHttpClient;
  @NotNull
  private final Executor myExecutor;
  @NotNull
  private final AtomicBoolean myInterruptedFlag;

  public ParallelDownloadContext(@NotNull S3DownloadConfiguration configuration,
                                 @NotNull AgentRunningBuild runningBuild,
                                 @NotNull FileSplitter fileSplitter,
                                 @NotNull S3HttpClient httpClient,
                                 @NotNull Executor executor,
                                 @NotNull AtomicBoolean interruptedFlag) {
    myConfiguration = configuration;
    myRunningBuild = runningBuild;
    myFileSplitter = fileSplitter;
    myHttpClient = httpClient;
    myExecutor = executor;
    myInterruptedFlag = interruptedFlag;
  }

  @NotNull
  public S3DownloadConfiguration getConfiguration() {
    return myConfiguration;
  }

  @NotNull
  public AgentRunningBuild getRunningBuild() {
    return myRunningBuild;
  }

  @NotNull
  public FileSplitter getFileSplitter() {
    return myFileSplitter;
  }

  @NotNull
  public S3HttpClient getHttpClient() {
    return myHttpClient;
  }

  @NotNull
  public Executor getExecutor() {
    return myExecutor;
  }

  @NotNull
  public AtomicBoolean getInterruptedFlag() {
    return myInterruptedFlag;
  }
}
