package jetbrains.buildServer.artifacts.s3.publish.errors;

import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ResponseAdapter {
  @NotNull
  public Map<String, String> getHeaders();

  @Nullable
  public String getHeader(@NotNull final String header);

  @Nullable
  public String getResponse();

  int getStatusCode();
}
