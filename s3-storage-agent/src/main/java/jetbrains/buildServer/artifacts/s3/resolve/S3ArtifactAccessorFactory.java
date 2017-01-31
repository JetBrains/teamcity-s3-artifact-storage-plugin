package jetbrains.buildServer.artifacts.s3.resolve;

import com.amazonaws.services.s3.AmazonS3;
import java.util.Map;
import jetbrains.buildServer.agent.artifacts.AgentExternalArtifactHelper;
import jetbrains.buildServer.artifacts.ArtifactAccessor;
import jetbrains.buildServer.artifacts.ArtifactAccessorFactoryExtension;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.S3Util;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  public ArtifactAccessor getArtifactAccessor(@NotNull final Map<String, String> params) {
    AmazonS3 client = S3Util.createAmazonClient(params);
    return new S3ArtifactAccessor(client, myHelper);
  }

  @NotNull
  public String getType() {
    return S3Constants.S3_STORAGE_TYPE;
  }

}
