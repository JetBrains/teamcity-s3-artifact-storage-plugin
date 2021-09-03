package jetbrains.buildServer.artifacts.s3;

import com.amazonaws.SdkClientException;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.intellij.openapi.diagnostic.Logger;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import jetbrains.buildServer.artifacts.ArtifactTransportAdditionalHeadersProvider;
import org.jetbrains.annotations.NotNull;

public class S3AdditionalHeadersProvider implements ArtifactTransportAdditionalHeadersProvider {
  @NotNull
  @Override
  public List<Header> getHeaders(@NotNull Configuration configuration) {

    return new ArrayList<>();
  }
}
