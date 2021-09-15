package jetbrains.buildServer.artifacts.s3;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import jetbrains.buildServer.artifacts.ArtifactTransportAdditionalHeadersProvider;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.executors.ExecutorsFactory;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpVersion;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.jetbrains.annotations.NotNull;

public class S3AdditionalHeadersProvider implements ArtifactTransportAdditionalHeadersProvider {

  private static final Logger LOG = Logger.getInstance(S3AdditionalHeadersProvider.class.getName());

  private volatile Region myRegion;

  private static final String AMAZON_METADATA_ADDRESS = "http://169.254.169.254/";
  private volatile boolean isAmazonAvailable = false;

  public S3AdditionalHeadersProvider() {
    initAvailabilityCheck();
  }

  private void initAvailabilityCheck() {
    ExecutorsFactory.newSingleThreadedExecutor("Agent Amazon Availability Check").submit(() -> {
      try {
        final HttpClient client = new HttpClient();
        client.getParams().setVersion(HttpVersion.HTTP_1_1);

        final HttpConnectionParams params = client.getHttpConnectionManager().getParams();

        int timeout = TeamCityProperties.getInteger(S3Constants.S3_AMAZON_METADATA_REQUEST_TIMEOUT_IN_SECONDS, 7);
        //timeout to wait for EC2 response
        params.setConnectionTimeout(timeout * 1000);
        //timeout to wait for data
        params.setSoTimeout(timeout * 10 * 1000);

        int code = client.executeMethod(new GetMethod(AMAZON_METADATA_ADDRESS));
        String msg;
        if (code == 200) {
          isAmazonAvailable = true;
          msg = "Amazon EC2 integration confirmed";
        } else {
          msg = String.format("Amazon EC2 integration is not active. Received error code %d while checking connection with Amazon metadata server", code);
        }
        LOG.debug(msg);
      } catch (IOException e) {
        String msg = "Amazon EC2 integration is not active: Failed to connect to " + AMAZON_METADATA_ADDRESS + ". " + e.getMessage();
        LOG.debug(msg, e);
        isAmazonAvailable = false;
      }
    });
  }

  @NotNull
  @Override
  public List<Header> getHeaders(@NotNull Configuration configuration) {
    ArrayList<Header> headers = new ArrayList<>();

    if (isAmazonAvailable) {
      Region region = getRegion();
      if (region != null) {
        headers.add(new Header(S3Constants.S3_REGION_HEADER_NAME, region.getName()));
      }
    }
    return headers;
  }

  private Region getRegion() {
    if (myRegion == null) {
      myRegion = Regions.getCurrentRegion();
    }
    return myRegion;
  }
}
