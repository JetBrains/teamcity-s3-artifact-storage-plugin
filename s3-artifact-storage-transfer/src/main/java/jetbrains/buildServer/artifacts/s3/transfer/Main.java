package jetbrains.buildServer.artifacts.s3.transfer;

import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import jetbrains.buildServer.serverSide.FileWatchingPropertiesModel;
import jetbrains.buildServer.serverSide.TeamCityProperties;

import static jetbrains.buildServer.artifacts.s3.transfer.settings.ArtifactTransferConstants.DEFAULT_PROCESSING_THREAD_COUNT;
import static jetbrains.buildServer.artifacts.s3.transfer.settings.ArtifactTransferConstants.PROCESSING_THREAD_COUNT;

public class Main {
  private final static Logger LOG = Logger.getInstance(ProjectProcessor.class.getName());

  public static void main(String[] args) throws IOException {
    if ((args.length == 1 && args[0].equals("--help")) || args.length < 3) {
      System.out.println("Please provide 3 command line parameters: project ID, source storage name, and target storage name");
      return;
    }

    String projectID = args[0];
    String source = args[1];
    String target = args[2];

    String propertiesFile = "config/application.properties";
    if (args.length > 3) {
      propertiesFile = args[3];
    }

    FileWatchingPropertiesModel propertiesModel = FileWatchingPropertiesModel.fromProperties(new File(propertiesFile));
    new TeamCityProperties() {{
      setModel(propertiesModel);
    }};

    ExecutorService executor = Executors.newFixedThreadPool(TeamCityProperties.getInteger(PROCESSING_THREAD_COUNT, DEFAULT_PROCESSING_THREAD_COUNT));

    try {
      ProjectProcessor processor = new ProjectProcessor(new TeamCityClient(), executor);

      processor.process(projectID, source, target);
    } catch (ExecutionException | InterruptedException | TimeoutException e) {
      LOG.error(e);
    } finally {
      executor.shutdown();
    }
  }
}
