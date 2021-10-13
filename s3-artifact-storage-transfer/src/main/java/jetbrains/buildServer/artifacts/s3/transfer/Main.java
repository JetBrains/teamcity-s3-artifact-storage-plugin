package jetbrains.buildServer.artifacts.s3.transfer;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.serverSide.TeamCityProperties;

public class Main {

  public static void main(String[] args) throws IOException {
    new TeamCityProperties() {{
      setBaseModel();
    }};
    //TODO read parameters from properties or input args
    Parameters parameters = new Parameters(
      "DefaultStorage",
      S3Constants.S3_STORAGE_TYPE,
      "eyJ0eXAiOiAiVENWMiJ9.SjVEVVQzdGw2Q09RZkd0aUhyUUlkR3hBU3U0.ZjViY2E4OGItN2JiYS00N2UzLWEzMTUtYmMyMjAwNDRiNmEy"
    );

    //TODO read project Id from parameters
    String projectId = "Test";

    //TODO read thread count from properties or input args
    ExecutorService executor = Executors.newFixedThreadPool(4);

    try {
      ProjectProcessor processor = new ProjectProcessor(new TeamCityClient(parameters), executor);

      processor.process(projectId, parameters);
    } finally {
      executor.shutdown();
    }
  }
}
