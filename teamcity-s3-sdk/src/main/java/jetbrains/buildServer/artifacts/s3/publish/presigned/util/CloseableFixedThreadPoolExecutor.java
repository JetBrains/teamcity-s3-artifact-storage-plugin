package jetbrains.buildServer.artifacts.s3.publish.presigned.util;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import jetbrains.buildServer.util.NamedThreadFactory;

public class CloseableFixedThreadPoolExecutor extends ThreadPoolExecutor implements AutoCloseable {
  private final boolean shouldInterrupt;

  public CloseableFixedThreadPoolExecutor(final int parallelism, String threadNamePrefix, boolean shouldInterrupt) {
    super(parallelism, parallelism,
          0L, TimeUnit.MILLISECONDS,
          new LinkedBlockingQueue<Runnable>(),
          new NamedThreadFactory(threadNamePrefix));
    this.shouldInterrupt = shouldInterrupt;
  }

  @Override
  public void close() {
    if (shouldInterrupt) {
      shutdownNow();
    } else {
      shutdown();
    }
  }
}
