package jetbrains.buildServer.artifacts.s3;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import java.util.ArrayList;
import java.util.List;
import jetbrains.buildServer.artifacts.ArtifactTransportAdditionalHeadersProvider;
import org.jetbrains.annotations.NotNull;

public class S3AdditionalHeadersProvider implements ArtifactTransportAdditionalHeadersProvider {

      @NotNull
      @Override
      public List<Header> getHeaders(@NotNull Configuration configuration) {
        List<Header> headers = new ArrayList<>();
        Region currentRegion = Regions.getCurrentRegion();
        if (currentRegion != null) {
          headers.add(new Header(S3Constants.S3_REGION_HEADER_NAME, currentRegion.getName()));
        }
        return headers;
      }
}
