package jetbrains.buildServer.artifacts.s3.retry;

import com.amazonaws.services.s3.model.AmazonS3Exception;
import java.util.concurrent.Callable;
import org.jetbrains.annotations.NotNull;

public class AbortingListener extends AbstractRetrierEventListener {
  @Override
  public <T> void onFailure(@NotNull final Callable<T> callable,
                            final int retry,
                            @NotNull final Exception e) {
    if (e instanceof AmazonS3Exception) {
      throw new RuntimeException(e);
    }
  }
}
