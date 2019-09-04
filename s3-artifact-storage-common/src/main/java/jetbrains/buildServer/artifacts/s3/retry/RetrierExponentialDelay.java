package jetbrains.buildServer.artifacts.s3.retry;

import java.util.concurrent.Callable;
import jetbrains.buildServer.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitrii Bogdanov
 */
public class RetrierExponentialDelay extends AbstractRetrierEventListener {
  private final long myDelay;

  public RetrierExponentialDelay(final long delayMs) {
    myDelay = delayMs;
  }

  @Override
  public <T> void onFailure(@NotNull final Callable<T> callable, final int retry, @NotNull final Exception e) {
    if (myDelay > 0) {
      try {
        Thread.sleep(myDelay * (retry + 1));
      } catch (InterruptedException ex) {
        ExceptionUtil.rethrowAsRuntimeException(e);
      }
    }
  }
}
