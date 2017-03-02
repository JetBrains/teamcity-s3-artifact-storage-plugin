package jetbrains.buildServer.artifacts.s3.resolve;

import jetbrains.buildServer.agent.artifacts.AgentExternalArtifactHelper;
import jetbrains.buildServer.artifacts.ArtifactAccessor;
import jetbrains.buildServer.artifacts.ArtifactAccessorFactoryExtension;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Created by Nikita.Skvortsov
 * date: 27.01.2017.
 */
public class S3ArtifactAccessorFactory implements ArtifactAccessorFactoryExtension {

  private final AgentExternalArtifactHelper myHelper;

  public S3ArtifactAccessorFactory(@NonNls final AgentExternalArtifactHelper helper) {
    myHelper = helper;
  }

  @Nullable
  public ArtifactAccessor createArtifactAccessor(@NotNull final Map<String, String> params) {
    return new S3ArtifactAccessor(params, myHelper);
  }

  @NotNull
  public String getType() {
    return S3Constants.S3_STORAGE_TYPE;
  }

}
