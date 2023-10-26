package jetbrains.buildServer.artifacts.s3;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import java.util.ArrayList;
import java.util.List;
import jetbrains.buildServer.agent.AgentLifeCycleAdapter;
import jetbrains.buildServer.agent.AgentLifeCycleListener;
import jetbrains.buildServer.agent.BuildAgent;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.artifacts.ArtifactTransportAdditionalHeadersProvider;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

public class S3AdditionalHeadersProvider implements ArtifactTransportAdditionalHeadersProvider {

  private final BuildAgentConfiguration myBuildAgentConfiguration;

  private static final String EC2_INSTANCE_ID_KEY = "ec2.instance-id";

  private volatile Region myRegion;

  private volatile boolean isAmazonAvailable = false;

  public S3AdditionalHeadersProvider(@NotNull EventDispatcher<AgentLifeCycleListener> eventDispatcher,
                                     @NotNull BuildAgentConfiguration buildAgentConfiguration) {
    myBuildAgentConfiguration = buildAgentConfiguration;
    eventDispatcher.addListener(new AgentLifeCycleAdapter(){
      @Override
      public void agentInitialized(@NotNull BuildAgent agent) {
        initAvailabilityCheck();
      }
    });
  }

  private void initAvailabilityCheck() {
    isAmazonAvailable = !StringUtil.isEmpty(myBuildAgentConfiguration.getBuildParameters().getSystemProperties().get(EC2_INSTANCE_ID_KEY));
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
