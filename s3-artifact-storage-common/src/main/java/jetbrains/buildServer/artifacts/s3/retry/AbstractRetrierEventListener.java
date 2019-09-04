package jetbrains.buildServer.artifacts.s3.retry;

import java.util.concurrent.Callable;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitrii Bogdanov
 */
public abstract class AbstractRetrierEventListener implements RetrierEventListener {
  @Override
  public <T> void beforeExecution(@NotNull final Callable<T> callable) {
  }

  @Override
  public <T> void afterExecution(@NotNull final Callable<T> callable) {
  }

  @Override
  public <T> void beforeRetry(@NotNull final Callable<T> callable, final int retry) {
  }

  @Override
  public <T> void onSuccess(@NotNull final Callable<T> callable, final int retry) {
  }

  @Override
  public <T> void onFailure(@NotNull final Callable<T> callable, final int retry, @NotNull final Exception e) {
  }
}
