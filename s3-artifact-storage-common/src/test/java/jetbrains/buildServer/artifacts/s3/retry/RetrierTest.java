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

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Dmitrii Bogdanov
 */
@SuppressWarnings({"UndeclaredTests"})
@Test
public class RetrierTest {
  private final CountingRetrier myCountingRetrier = new CountingRetrier();

  @BeforeMethod
  public void setUp() {
    myCountingRetrier.reset();
  }

  @Test
  void testNumberOfRetriesIsCorrect() {
    final CountingRetrier counter = new CountingRetrier();
    try {
      new RetrierImpl(5)
        .registerListener(counter)
        .execute(new Callable<Integer>() {
          @Override
          public Integer call() {
            throw new DummyRuntimeException("Oops!");
          }
        });
    } catch (Exception ignored) {
    }
    Assert.assertEquals(counter.getNumberOfFailures(), 6);
    Assert.assertEquals(counter.getNumberOfRetries(), 5);
  }

  @Test
  void testRuntimeExceptionThrownUnchanged() {
    final DummyRuntimeException expected = new DummyRuntimeException("Oops!");
    try {
      new RetrierImpl(1).execute((Callable<Integer>)() -> {
        throw expected;
      });
    } catch (Exception actual) {
      Assert.assertEquals(actual, expected);
    }
  }

  @Test
  void testCheckedExceptionThrownAsRuntimeWithSameMessage() {
    try {
      new RetrierImpl(2).execute(new Callable<Integer>() {
        @Override
        public Integer call() throws DummyCheckedException {
          throw new DummyCheckedException("Oops!");
        }
      });
    } catch (Exception e) {
      Assert.assertEquals(e.getClass(), RuntimeException.class);
      Assert.assertEquals(e.getMessage(), "Oops!");
      Assert.assertEquals(e.getCause().getClass(), DummyCheckedException.class);
      Assert.assertEquals(e.getCause().getMessage(), "Oops!");
    }
    try {
      new RetrierImpl(2).execute(new Callable<Integer>() {
        @Override
        public Integer call() throws DummyCheckedException {
          throw new DummyCheckedException(null);
        }
      });
    } catch (Exception e) {
      Assert.assertEquals(e.getClass(), RuntimeException.class);
      Assert.assertNull(e.getMessage());
      Assert.assertEquals(e.getCause().getClass(), DummyCheckedException.class);
      Assert.assertNull(e.getCause().getMessage());
    }
  }

  @Test
  void testRetrierReturnsCorrectResultWhenFailedLessTimesThanNumberOfRetries() {
    final String result = new RetrierImpl(10)
      .registerListener(myCountingRetrier)
      .execute(failingNTimes(1, "expected"));
    Assert.assertEquals(result, "expected");
    Assert.assertEquals(myCountingRetrier.getNumberOfFailures(), 1);
    Assert.assertEquals(myCountingRetrier.getNumberOfRetries(), 1);
  }

  @SuppressWarnings("SameParameterValue")
  @NotNull
  private <T> Callable<T> failingNTimes(final int nTimesToFail, final T retVal) {
    return new Callable<T>() {
      @Override
      public T call() {
        if (myCountingRetrier.getNumberOfFailures() < nTimesToFail) {
          throw new DummyRuntimeException("Oops!");
        } else {
          return retVal;
        }
      }
    };
  }

  private static final class DummyRuntimeException extends RuntimeException {
    public DummyRuntimeException(final String message) {
      super(message);
    }
  }

  private static final class DummyCheckedException extends Exception {
    public DummyCheckedException(@Nullable final String message) {
      super(message);
    }
  }


  /**
   * @author Dmitrii Bogdanov
   */
  public static class CountingRetrier extends AbstractRetrierEventListener {
    private final AtomicInteger myNumberOfRetries = new AtomicInteger();
    private final AtomicInteger myNumberOfFailures = new AtomicInteger(0);
    private final AtomicLong myExecutionTimeMs = new AtomicLong();

    @Override
    public <T> void beforeExecution(@NotNull final Callable<T> callable) {
      myExecutionTimeMs.set(System.currentTimeMillis());
    }

    @Override
    public <T> void beforeRetry(@NotNull final Callable<T> callable, final int retry) {
      myNumberOfRetries.set(retry);
    }

    @Override
    public <T> void afterExecution(@NotNull final Callable<T> callable) {
      myExecutionTimeMs.addAndGet(-System.currentTimeMillis());
    }

    @Override
    public <T> void onFailure(@NotNull final Callable<T> callable, final int retry, @NotNull final Exception e) {
      myNumberOfFailures.incrementAndGet();
    }

    int getNumberOfRetries() {
      return myNumberOfRetries.get();
    }

    int getNumberOfFailures() {
      return myNumberOfFailures.get();
    }

    long getExecutionTimeInMs() {
      return -myExecutionTimeMs.get();
    }

    public void reset() {
      myNumberOfRetries.set(0);
      myNumberOfFailures.set(0);
      myExecutionTimeMs.set(0);
    }
  }
}
