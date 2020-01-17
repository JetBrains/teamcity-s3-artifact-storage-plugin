/*
 * Copyright 2000-2020 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
