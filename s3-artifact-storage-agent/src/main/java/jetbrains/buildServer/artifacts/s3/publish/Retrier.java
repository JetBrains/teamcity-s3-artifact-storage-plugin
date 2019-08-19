package jetbrains.buildServer.artifacts.s3.publish;

import com.intellij.openapi.diagnostic.Logger;
import java.util.concurrent.Callable;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitrii Bogdanov
 */
public class Retrier {
  private final int myMaxRetries;
  private final Logger myLogger;

  public Retrier(final int maxRetries, @NotNull final Logger logger) {
    if (maxRetries < 0) {
      throw new IllegalArgumentException("Number of retries should be greater than 0");
    }
    myMaxRetries = maxRetries;
    myLogger = logger;
  }

  public <T> T execute(@NotNull final Callable<T> callable) {
    RuntimeException exception = null;
    for (int retry = 0; retry <= myMaxRetries; retry++) {
      try {
        if (myLogger.isDebugEnabled()) {
          myLogger.debug("Calling [" + callable + "], retry: " + retry + ".");
        }
        final T call = callable.call();
        if (myLogger.isDebugEnabled()) {
          myLogger.debug("Calling [" + callable + "], retry: " + retry + " successful.");
        }
        return call;
      } catch (Exception e) {
        if (myLogger.isDebugEnabled()) {
          myLogger.debug("Calling [" + callable + "], retry: " + retry + " failed with exception.", e);
        }
        if (exception == null) {
          if (exception instanceof RuntimeException) {
            exception = (RuntimeException)e;
          } else {
            exception = new RuntimeException(e);
          }
        }
      }
    }
    assert exception != null : "If we got here, exception cannot be null";
    throw exception;
  }
}
