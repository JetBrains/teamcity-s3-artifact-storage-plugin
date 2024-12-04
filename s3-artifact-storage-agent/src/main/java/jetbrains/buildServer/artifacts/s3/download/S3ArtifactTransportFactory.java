package jetbrains.buildServer.artifacts.s3.download;

import com.intellij.openapi.diagnostic.Logger;
import java.util.*;
import java.util.concurrent.ExecutorService;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.artifacts.TransportFactoryExtension;
import jetbrains.buildServer.artifacts.URLContentRetriever;
import jetbrains.buildServer.artifacts.impl.DependencyHttpHelper;
import jetbrains.buildServer.artifacts.impl.HttpArtifactTransportFactory;
import jetbrains.buildServer.artifacts.s3.download.parallel.strategy.ParallelDownloadStrategy;
import jetbrains.buildServer.http.HttpUtil;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.ThreadUtil;
import jetbrains.buildServer.util.executors.ExecutorsFactory;
import jetbrains.buildServer.util.positioning.PositionAware;
import jetbrains.buildServer.util.positioning.PositionAwareSortedCollection;
import jetbrains.buildServer.util.positioning.PositionConstraint;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

public class S3ArtifactTransportFactory extends AgentLifeCycleAdapter implements TransportFactoryExtension, PositionAware, InitializingBean, DisposableBean {
  private static final Logger LOGGER = Logger.getInstance(S3ArtifactTransportFactory.class);
  private static final String EXECUTOR_NAME = "S3 artifact transport executor";

  @NotNull
  private final DependencyHttpHelper myDependencyHttpHelper;
  @NotNull
  private final HttpArtifactTransportFactory myDefaultTransportFactory;
  @NotNull
  private final CurrentBuildTracker myCurrentBuildTracker;
  @NotNull
  private final EventDispatcher<AgentLifeCycleListener> myAgentLifecycleDispatcher;
  @NotNull
  private final List<ParallelDownloadStrategy> myParallelDownloadStrategies;
  @NotNull
  private final String mySimpleClassName = S3ArtifactTransportFactory.class.getSimpleName();

  // state fields, access should be synchronized by this
  @Nullable
  private volatile S3DownloadConfiguration myCurrentBuildConfiguration;
  @NotNull
  private final Map<Map<String, String>, HttpClient> myCurrentBuildClients = new HashMap<>(); // clients pool to reuse diring a build
  @Nullable
  private volatile ExecutorService myExecutor;
  private volatile int myExecutorParallelism;
  private volatile boolean myFactoryShutdown = false;

  public S3ArtifactTransportFactory(@NotNull DependencyHttpHelper dependencyHttpHelper,
                                    @NotNull HttpArtifactTransportFactory defaultTransportFactory,
                                    @NotNull CurrentBuildTracker currentBuildTracker,
                                    @NotNull EventDispatcher<AgentLifeCycleListener> agentLifecycleDispatcher,
                                    @NotNull List<ParallelDownloadStrategy> parallelDownloadStrategies) {
    myDependencyHttpHelper = dependencyHttpHelper;
    myDefaultTransportFactory = defaultTransportFactory;
    myCurrentBuildTracker = currentBuildTracker;
    myAgentLifecycleDispatcher = agentLifecycleDispatcher;
    myParallelDownloadStrategies = parallelDownloadStrategies;
  }

  @Override
  public void afterPropertiesSet() {
    myAgentLifecycleDispatcher.addListener(this);
  }

  @Override
  public void destroy() {
    shutdown();
    myAgentLifecycleDispatcher.removeListener(this);
  }

  private synchronized void shutdown() {
    if (myFactoryShutdown) return;
    disposeExecutor(true);
    disposeClients(true);
    myFactoryShutdown = true;
  }

  @Override
  public synchronized void buildStarted(@NotNull AgentRunningBuild runningBuild) {
    if (myFactoryShutdown) return;
    ensurePreparedToBuild(runningBuild);
  }

  private synchronized S3DownloadConfiguration ensurePreparedToBuild(@NotNull AgentRunningBuild runningBuild) {
    S3DownloadConfiguration currentConfiguration = myCurrentBuildConfiguration;
    if (currentConfiguration != null && currentConfiguration.getBuildId() == runningBuild.getBuildId()) {
      return currentConfiguration; // already prepared to this build
    }

    S3DownloadConfiguration newConfiguration = new S3DownloadConfiguration(runningBuild);
    myCurrentBuildConfiguration = newConfiguration;
    ensureExecutorReady(newConfiguration);
    disposeClients(false);
    return newConfiguration;
  }

  private synchronized void ensureExecutorReady(@NotNull S3DownloadConfiguration configuration) {
    // reinit executor if parallelism changed since the last build
    int parallelism = configuration.getMaxThreads();
    if (myExecutor != null) {
      if (myExecutorParallelism == parallelism) {
        return; // can reuse executor from the previous build
      }

      disposeExecutor(false);
    }

    myExecutor = ExecutorsFactory.newFixedDaemonExecutor(EXECUTOR_NAME, parallelism);
    myExecutorParallelism = parallelism;
  }

  private synchronized void disposeExecutor(boolean silently) {
    try {
      ExecutorService executor = myExecutor;
      if (executor == null) {
        return;
      }

      ThreadUtil.shutdownGracefully(executor, EXECUTOR_NAME);
      myExecutor = null;
      myExecutorParallelism = 0;
    } catch (RuntimeException e) {
      LOGGER.error(String.format("Error while shutting down %s", EXECUTOR_NAME), e);
      if (!silently) {
        throw new RuntimeException(String.format("Error while shutting down %s", EXECUTOR_NAME), e);
      }
    }
  }

  private synchronized void disposeClients(boolean silently) {
    Collection<HttpClient> clients = myCurrentBuildClients.values();
    clients.forEach(client -> {
      try {
        MultiThreadedHttpConnectionManager connectionManager = (MultiThreadedHttpConnectionManager)client.getHttpConnectionManager();
        connectionManager.shutdown();
      } catch (RuntimeException e) {
        LOGGER.error(String.format("Error while shutting down connection manager of %s", mySimpleClassName), e);
        if (!silently) {
          throw new RuntimeException("Error while shutting down connection manager", e);
        }
      }
    });
    myCurrentBuildClients.clear();
  }

  @Override
  public void beforeBuildFinish(@NotNull AgentRunningBuild build, @NotNull BuildFinishedStatus buildStatus) {
    cleanupBeforeBuildFinish();
  }

  private synchronized void cleanupBeforeBuildFinish() {
    if (myFactoryShutdown) return;
    disposeClients(false); // the next build will not be able to reuse any client because it will have the other user for authentication on server
    myCurrentBuildConfiguration = null;
  }

  @Override
  @Nullable
  public synchronized URLContentRetriever getTransport(@NotNull Map<String, String> parameters) {
    if (myFactoryShutdown) {
      LOGGER.warn("Attempted to create S3 artifact transport on shut down factory");
      return null;
    }

    AgentRunningBuild runningBuild;
    try {
      runningBuild = myCurrentBuildTracker.getCurrentBuild();
    } catch (NoRunningBuildException e) {
      LOGGER.warn("Attempted to create S3 artifact transport outside the scope of a running build");
      return null;
    }

    S3DownloadConfiguration configuration = ensurePreparedToBuild(runningBuild); // configuration, executor and clients map are prepared until we hold the lock
    boolean parallelDownloadEnabled = configuration.isParallelDownloadEnabled();
    boolean storageS3Compatible = configuration.isS3CompatibleStorage();
    boolean parallelDownloadForced = configuration.isParallelDownloadForced();
    long buildId = runningBuild.getBuildId();
    if (!parallelDownloadEnabled || !(storageS3Compatible || parallelDownloadForced)) {
      LOGGER.debug(String.format(
        "Will not create S3 artifact transport, build ID = %s, parallel download enabled = %s, storage is S3 compatible = %s, parallel download forced = %s",
        buildId, parallelDownloadEnabled, storageS3Compatible, parallelDownloadForced
      ));
      return null;
    }

    LOGGER.debug(String.format("Creating S3 artifact transport, build ID = %s", buildId));
    HttpClient client = findOrCreateClient(parameters, configuration);
    String serverUrl = parameters.get(DependencyHttpHelper.SERVER_URL_PARAM);
    ExecutorService executor = myExecutor;
    Objects.requireNonNull(executor, "Executor is null");
    return new S3ArtifactTransport(serverUrl, client, executor, myDependencyHttpHelper, configuration, runningBuild, myParallelDownloadStrategies);
  }

  @NotNull
  private synchronized HttpClient findOrCreateClient(@NotNull Map<String, String> parameters, @NotNull S3DownloadConfiguration configuration) {
    Map<String, String> parametersCopy = new HashMap<>(parameters); // copy to guarantee map keys don't mutate
    return myCurrentBuildClients.computeIfAbsent(parametersCopy, p -> {
      LOGGER.debug("Creating new HTTP client and adding it to the pool");
      return createClient(parametersCopy, configuration);
    });
  }

  @NotNull
  private HttpClient createClient(@NotNull Map<String, String> parameters, @NotNull S3DownloadConfiguration configuration) {
    HttpClient httpClient = myDependencyHttpHelper.createClient(parameters);
    if (httpClient == null) {
      throw new RuntimeException(String.format("%s failed to create HTTP client", DependencyHttpHelper.class.getSimpleName()));
    }

    long connectionManagerTimeoutMs = httpClient.getParams().getConnectionManagerTimeout();
    HttpConnectionManager connectionManager = HttpUtil.createMultiThreadedHttpConnectionManager((int)connectionManagerTimeoutMs / 1000);
    connectionManager.getParams().setDefaultMaxConnectionsPerHost(configuration.getMaxConnectionsPerHost());
    connectionManager.getParams().setMaxTotalConnections(configuration.getMaxConnectionsTotal());
    httpClient.setHttpConnectionManager(connectionManager);
    return httpClient;
  }

  @Override
  @NotNull
  public String getOrderId() {
    return getClass().getName();
  }

  @Override
  @NotNull
  public PositionConstraint getConstraint() {
    return new PositionConstraint() {
      @Override
      public String toString() {
        return "Before " + myDefaultTransportFactory.getOrderId();
      }

      @Override
      public int compareTo(String id) {
        return Integer.MAX_VALUE;
      }

      @Override
      public boolean isLast() {
        /**
         * Should be last to be sorted correctly because {@link myDefaultTransportFactory} is last, see {@link PositionAwareSortedCollection#getSorted()}
         */
        return true;
      }

      @NotNull
      @Override
      public Collection<String> getBefore() {
        return Collections.singleton(myDefaultTransportFactory.getOrderId());
      }
    };
  }
}
