package jetbrains.buildServer.artifacts.s3.resolve;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.CurrentBuildTracker;
import jetbrains.buildServer.agent.NoRunningBuildException;
import jetbrains.buildServer.artifacts.DependencyResolverContext;
import jetbrains.buildServer.artifacts.TransportFactoryExtension;
import jetbrains.buildServer.artifacts.URLContentRetriever;
import jetbrains.buildServer.log.Loggers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

import static com.amazonaws.regions.Regions.US_WEST_2;
import static jetbrains.buildServer.artifacts.s3.Constants.S3_KEY_ID;
import static jetbrains.buildServer.artifacts.s3.Constants.S3_SECRET_KEY;

/**
 * Created by Nikita.Skvortsov
 * date: 31.03.2016.
 */
public class S3TransportFactory implements TransportFactoryExtension {

  @NotNull
  private final CurrentBuildTracker myTracker;

  public S3TransportFactory(@NotNull CurrentBuildTracker buildTracker) {
    myTracker = buildTracker;
  }

  @Nullable
  @Override
  public URLContentRetriever getTransport(@NotNull DependencyResolverContext dependencyResolverContext) {
    try {
      final AgentRunningBuild currentBuild = myTracker.getCurrentBuild();

      final Map<String, String> params = currentBuild.getSharedConfigParameters();
      final String accessKeyId = params.get(S3_KEY_ID);
      final String secretAccessKey = params.get(S3_SECRET_KEY);
      AmazonS3 s3client = new AmazonS3Client(new BasicAWSCredentials(accessKeyId, secretAccessKey));
      s3client.setRegion(Region.getRegion(US_WEST_2));
      return new S3URLContentRetriever(s3client);
    } catch (NoRunningBuildException e) {
      Loggers.AGENT.warnAndDebugDetails("Attempted to create artifacts transport when no build is running.", e);
    }
    return null;
  }
}
