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
