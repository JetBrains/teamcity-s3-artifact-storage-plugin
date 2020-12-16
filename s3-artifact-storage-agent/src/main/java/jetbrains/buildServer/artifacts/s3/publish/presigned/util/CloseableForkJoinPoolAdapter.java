package jetbrains.buildServer.artifacts.s3.publish.presigned.util;

import com.intellij.openapi.diagnostic.Logger;
import java.util.concurrent.ForkJoinPool;
import org.jetbrains.annotations.NotNull;

public class CloseableForkJoinPoolAdapter extends ForkJoinPool implements AutoCloseable {
  @NotNull
  private static final Logger LOGGER = Logger.getInstance(CloseableForkJoinPoolAdapter.class);

  public CloseableForkJoinPoolAdapter(final int parallelism) {
    super(parallelism, defaultForkJoinWorkerThreadFactory, (t, e) -> {
      LOGGER.error("Got error while executing upload " + e.getMessage(), e);
    }, false);
  }

  @Override
  public void close() {
    shutdown();
  }
}
