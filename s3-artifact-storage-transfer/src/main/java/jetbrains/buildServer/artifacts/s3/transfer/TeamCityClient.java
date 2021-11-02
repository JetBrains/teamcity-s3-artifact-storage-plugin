package jetbrains.buildServer.artifacts.s3.transfer;

import java.io.IOException;
import java.util.List;
import jetbrains.buildServer.artifacts.s3.transfer.model.Build;
import jetbrains.buildServer.artifacts.s3.transfer.model.Project;
import org.jetbrains.annotations.NotNull;

public interface TeamCityClient {
  @NotNull
  Project getDetails(@NotNull String project) throws IOException;

  @NotNull
  List<String> getBuilds(@NotNull String project) throws IOException;

  @NotNull
  Build getArtifacts(@NotNull String buildId) throws IOException;
}
