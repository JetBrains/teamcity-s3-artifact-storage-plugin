package jetbrains.buildServer.artifacts.s3.publish.presigned.util;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import jetbrains.buildServer.util.executors.ExecutorsFactory;

public class CloseableS3SignedUrlUploadPool implements AutoCloseable {
  private final ExecutorService myExecutor;

  public CloseableS3SignedUrlUploadPool(final int parallelism) {
    myExecutor = ExecutorsFactory.newFixedExecutor("S3SignedUrlUpload", parallelism);
  }

  @Override
  public void close() {
    myExecutor.shutdown();
  }

  public <T> Future<T> submit(Callable<T> task) {
    return myExecutor.submit(task);
  }

  public void shutdownNow() {
    myExecutor.shutdownNow();
  }

  public boolean isShutdown() {
    return myExecutor.isShutdown();
  }

  public boolean isTerminated() {
    return myExecutor.isTerminated();
  }

}
