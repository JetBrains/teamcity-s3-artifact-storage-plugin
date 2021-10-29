package jetbrains.buildServer.artifacts.s3.transfer;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jetbrains.buildServer.serverSide.TeamCityProperties;

public class Main {

  public static void main(String[] args) throws IOException {
    new TeamCityProperties() {{
      setBaseModel();
    }};
    //TODO read parameters from properties or input args
    Parameters parameters = new Parameters(
      "default",
      "parentS3",
      "eyJ0eXAiOiAiVENWMiJ9.WG1EQzBwZXFSX0Z5bjVtT2NaVlZsTGlGYTZJ.ZWFiNjE2N2EtNjUxMi00NDM3LTlkZDEtYThjOGYzNDNkYzMx"
    );

    //TODO read project Id from parameters
    String projectId = "Test";

    //TODO read thread count from properties or input args
    ExecutorService executor = Executors.newFixedThreadPool(4);

    try {
      ProjectProcessor processor = new ProjectProcessor(new TeamCityClient(parameters.getToken()), executor);

      processor.process(projectId, parameters);
    } catch (ExecutionException e) {
      e.printStackTrace();
    } catch (InterruptedException e) {
      e.printStackTrace();
    } finally {
      executor.shutdown();
    }
  }
}
