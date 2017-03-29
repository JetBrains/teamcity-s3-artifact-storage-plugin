package jetbrains.buildServer.artifacts.s3.resolve;

import jetbrains.buildServer.agent.CurrentBuildTracker;
import jetbrains.buildServer.agent.artifacts.AgentArtifactHelper;
import jetbrains.buildServer.artifacts.ArtifactAccessor;
import jetbrains.buildServer.artifacts.ArtifactAccessorFactoryExtension;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Created by Nikita.Skvortsov
 * date: 27.01.2017.
 */
public class S3ArtifactAccessorFactory implements ArtifactAccessorFactoryExtension {

  @NotNull private final AgentArtifactHelper myHelper;
  @NotNull private final CurrentBuildTracker myCurrentBuildTracker;

  public S3ArtifactAccessorFactory(@NotNull final AgentArtifactHelper helper, @NotNull CurrentBuildTracker currentBuildTracker) {
    myHelper = helper;
    myCurrentBuildTracker = currentBuildTracker;
  }

  @Nullable
  public ArtifactAccessor createArtifactAccessor(@NotNull final Map<String, String> params) {
    return new S3ArtifactAccessor(params, myCurrentBuildTracker, myHelper);
  }

  @NotNull
  public String getType() {
    return S3Constants.S3_STORAGE_TYPE;
  }

}
