package jetbrains.buildServer.artifacts.s3.retry;

import java.util.concurrent.Callable;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitrii Bogdanov
 */
public interface RetrierEventListener {
  <T> void beforeExecution(@NotNull Callable<T> callable);

  <T> void afterExecution(@NotNull Callable<T> callable);

  <T> void beforeRetry(@NotNull Callable<T> callable, int retry);

  <T> void onSuccess(@NotNull Callable<T> callable, int retry);

  <T> void onFailure(@NotNull Callable<T> callable, int retry, @NotNull Exception e);
}
