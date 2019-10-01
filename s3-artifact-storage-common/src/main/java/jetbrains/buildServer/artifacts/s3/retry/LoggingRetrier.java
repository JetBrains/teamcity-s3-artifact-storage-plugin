package jetbrains.buildServer.artifacts.s3.retry;

import com.intellij.openapi.diagnostic.Logger;
import java.util.concurrent.Callable;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitrii Bogdanov
 */
public class LoggingRetrier extends AbstractRetrierEventListener {
  private final Logger myLogger;

  public LoggingRetrier(@NotNull final Logger logger) {
    myLogger = logger;
  }

  @Override
  public <T> void beforeRetry(@NotNull final Callable<T> callable, final int retry) {
    if (myLogger.isDebugEnabled()) {
      myLogger.debug("Calling [" + callable + "], retry: " + retry + ".");
    }
  }

  @Override
  public <T> void onSuccess(@NotNull final Callable<T> callable, final int retry) {
    if (myLogger.isDebugEnabled()) {
      myLogger.debug("Calling [" + callable + "], retry: " + retry + " successful.");
    }
  }

  @Override
  public <T> void onFailure(@NotNull final Callable<T> callable, final int retry, @NotNull final Exception e) {
    myLogger.info("Calling [" + callable + "], retry: " + retry + " failed with exception.", e);
  }
}
