package jetbrains.buildServer.artifacts.s3.publish;

import java.util.concurrent.ForkJoinPool;

public class CloseableForkJoinPoolAdapter extends ForkJoinPool implements AutoCloseable {
  public CloseableForkJoinPoolAdapter(int parallelism) {
    super(parallelism);
  }

  @Override
  public void close() {
    shutdown();
  }
}
