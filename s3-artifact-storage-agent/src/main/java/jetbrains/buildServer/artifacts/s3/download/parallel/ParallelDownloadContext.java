package jetbrains.buildServer.artifacts.s3.download.parallel;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.artifacts.s3.download.S3DownloadConfiguration;
import jetbrains.buildServer.artifacts.s3.download.S3HttpClient;
import org.jetbrains.annotations.NotNull;

public final class ParallelDownloadContext {
  @NotNull
  private final S3DownloadConfiguration configuration;
  @NotNull
  private final AgentRunningBuild runningBuild;
  @NotNull
  private final S3HttpClient httpClient;
  @NotNull
  private final Executor executor;
  @NotNull
  private final AtomicBoolean interruptedFlag;

  public ParallelDownloadContext(@NotNull S3DownloadConfiguration configuration,
                                 @NotNull AgentRunningBuild runningBuild,
                                 @NotNull S3HttpClient httpClient,
                                 @NotNull Executor executor,
                                 @NotNull AtomicBoolean interruptedFlag) {
    this.configuration = configuration;
    this.runningBuild = runningBuild;
    this.httpClient = httpClient;
    this.executor = executor;
    this.interruptedFlag = interruptedFlag;
  }

  @NotNull
  public S3DownloadConfiguration getConfiguration() {
    return configuration;
  }

  @NotNull
  public AgentRunningBuild getRunningBuild() {
    return runningBuild;
  }

  @NotNull
  public S3HttpClient getHttpClient() {
    return httpClient;
  }

  @NotNull
  public Executor getExecutor() {
    return executor;
  }

  @NotNull
  public AtomicBoolean getInterruptedFlag() {
    return interruptedFlag;
  }
}
