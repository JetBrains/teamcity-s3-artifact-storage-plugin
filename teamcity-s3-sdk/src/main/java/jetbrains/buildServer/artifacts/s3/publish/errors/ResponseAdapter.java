package jetbrains.buildServer.artifacts.s3.publish.errors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ResponseAdapter {

  @Nullable
  String getHeader(@NotNull final String header);

  @Nullable
  String getResponse();

  int getStatusCode();
}
