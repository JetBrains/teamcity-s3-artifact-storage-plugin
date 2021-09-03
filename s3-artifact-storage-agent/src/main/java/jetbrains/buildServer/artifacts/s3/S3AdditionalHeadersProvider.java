package jetbrains.buildServer.artifacts.s3;

import com.amazonaws.SdkClientException;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.util.EC2MetadataUtils;
import com.intellij.openapi.diagnostic.Logger;
import java.util.ArrayList;
import java.util.List;
import jetbrains.buildServer.artifacts.ArtifactTransportAdditionalHeadersProvider;
import org.jetbrains.annotations.NotNull;

public class S3AdditionalHeadersProvider implements ArtifactTransportAdditionalHeadersProvider {
  private static final Logger LOG = Logger.getInstance(S3AdditionalHeadersProvider.class.getName());

  private volatile boolean myIsRunningInAws = true;

  @NotNull
  @Override
  public List<Header> getHeaders(@NotNull Configuration configuration) {
    List<Header> headers = new ArrayList<>();

    if (myIsRunningInAws) {
      try {
        Region currentRegion = Regions.getCurrentRegion();
        if (currentRegion != null) {
          headers.add(new Header(S3Constants.S3_REGION_HEADER_NAME, currentRegion.getName()));
        }
      } catch (SdkClientException e) {
        String msg = "Amazon EC2 integration is not active: Failed to connect. " + e.getMessage();
        LOG.debug(msg, e);
        myIsRunningInAws = false;
      }
    }
    return headers;
  }
}
