package jetbrains.buildServer.artifacts.s3.transfer.storage;

import java.io.File;
import jetbrains.buildServer.artifacts.s3.transfer.model.Build;
import jetbrains.buildServer.artifacts.s3.transfer.model.Feature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface Storage {
  @NotNull
  String getType();

  @Nullable
  File download(@NotNull String artifact, @NotNull Build metadata);

  void upload(@NotNull File artifact, @NotNull Build metadata);

  void delete(@NotNull File artifact);

  @NotNull
  String getFeatureId();

  void init(Feature feature);
}
