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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitrii Bogdanov
 */
public class RetrierImpl implements Retrier {
  private final int myMaxRetries;
  private final List<RetrierEventListener> myRetrierEventListeners = new ArrayList<RetrierEventListener>();

  public RetrierImpl(final int maxRetries) {
    if (maxRetries < 0) {
      throw new IllegalArgumentException("Number of retries should be greater than 0");
    }
    myMaxRetries = maxRetries;
  }

  @Override
  public <T> T execute(@NotNull final Callable<T> callable) {
    beforeExecution(callable);
    RuntimeException exception = null;
    for (int retry = 0; retry <= myMaxRetries; retry++) {
      try {
        beforeRetry(callable, retry);
        final T call = callable.call();
        onSuccess(callable, retry);
        afterExecution(callable);
        return call;
      } catch (Exception e) {
        if (exception == null) {
          exception = e instanceof RuntimeException ? (RuntimeException)e : new RuntimeException(e.getMessage(), e);
        }
        onFailure(callable, retry, e);
      }
    }
    afterExecution(callable);
    assert exception != null : "If we got here, exception cannot be null";
    throw exception;
  }

  @NotNull
  @Override
  public Retrier registerListener(@NotNull final RetrierEventListener retrierEventListener) {
    myRetrierEventListeners.add(retrierEventListener);
    return this;
  }

  @Override
  public <T> void beforeExecution(@NotNull final Callable<T> callable) {
    for (final RetrierEventListener retrierEventListener : myRetrierEventListeners) {
      retrierEventListener.beforeExecution(callable);
    }
  }

  @Override
  public <T> void beforeRetry(@NotNull final Callable<T> callable, final int retry) {
    for (final RetrierEventListener retrierEventListener : myRetrierEventListeners) {
      retrierEventListener.beforeRetry(callable, retry);
    }
  }

  @Override
  public <T> void onSuccess(@NotNull final Callable<T> callable, final int retry) {
    for (final RetrierEventListener retrierEventListener : myRetrierEventListeners) {
      retrierEventListener.onSuccess(callable, retry);
    }
  }

  @Override
  public <T> void onFailure(@NotNull final Callable<T> callable, final int retry, @NotNull final Exception e) {
    for (final RetrierEventListener retrierEventListener : myRetrierEventListeners) {
      retrierEventListener.onFailure(callable, retry, e);
    }
  }

  @Override
  public <T> void afterExecution(@NotNull final Callable<T> callable) {
    for (final RetrierEventListener retrierEventListener : myRetrierEventListeners) {
      retrierEventListener.afterExecution(callable);
    }
  }
}
