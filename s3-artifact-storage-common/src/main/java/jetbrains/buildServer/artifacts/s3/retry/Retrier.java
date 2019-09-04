package jetbrains.buildServer.artifacts.s3.retry;

import java.util.concurrent.Callable;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitrii Bogdanov
 */
public interface Retrier extends RetrierEventListener {
  <T> T execute(@NotNull final Callable<T> callable);

  @NotNull
  Retrier registerListener(@NotNull RetrierEventListener retrier);
}
