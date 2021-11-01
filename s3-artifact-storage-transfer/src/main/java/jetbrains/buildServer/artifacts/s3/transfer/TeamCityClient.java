package jetbrains.buildServer.artifacts.s3.transfer;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import jetbrains.buildServer.artifacts.s3.transfer.model.Build;
import jetbrains.buildServer.artifacts.s3.transfer.model.Project;
import jetbrains.buildServer.artifacts.s3.transfer.settings.ArtifactTransferConstants;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;

public class TeamCityClient {
  public static final String DETAILS_URL =
    "http://localhost:8111/bs/app/rest/projects/%s/?fields=id,name,projectFeatures(projectFeature(id,type,properties(property(name,value)))),projects(project(id))";
  public static final String BUILDS_URL = "http://localhost:8111/bs/app/rest/builds/?fields=build(id)&locator=count:-1,project:%s";
  public static final String ARTIFACTS_URL = "http://localhost:8111/bs/app/rest/builds/%s/?fields=id,buildType,project,artifacts(file(name,fullName)),artifactsDirectory";

  @NotNull
  private static final ThreadLocal<ObjectMapper> MAPPER = ThreadLocal.withInitial(() -> new ObjectMapper()
    .registerModule(new JaxbAnnotationModule())
    .configure(SerializationFeature.WRAP_ROOT_VALUE, false)
    .configure(SerializationFeature.INDENT_OUTPUT, false)
    .configure(MapperFeature.USE_ANNOTATIONS, true)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
  );

  @NotNull
  private final String myToken;

  public TeamCityClient() {
    myToken = TeamCityProperties.getProperty(ArtifactTransferConstants.ACCESS_TOKEN);
  }

  @NotNull
  public Project getDetails(@NotNull String project) throws IOException {
    try (CloseableHttpClient client = HttpClients.createDefault()) {

      CloseableHttpResponse response = get(client, String.format(DETAILS_URL, project));

      String content = EntityUtils.toString(response.getEntity());
      ObjectMapper mapper = MAPPER.get();

      return mapper.readValue(content, Project.class);
    }
  }

  @NotNull
  public List<String> getBuilds(@NotNull String project) throws IOException {
    try (CloseableHttpClient client = HttpClients.createDefault()) {

      CloseableHttpResponse response = get(client, String.format(BUILDS_URL, project));

      String content = EntityUtils.toString(response.getEntity());
      ObjectMapper mapper = MAPPER.get();
      ArrayList<String> results = new ArrayList<>();
      JsonNode tree = mapper.readTree(content.getBytes(StandardCharsets.UTF_8));
      for (JsonNode jsonNode : tree.get("build")) {
        results.add(jsonNode.get("id").asText());
      }

      return results;
    }
  }

  @NotNull
  public Build getArtifacts(@NotNull String buildId) throws IOException {
    try (CloseableHttpClient client = HttpClients.createDefault()) {

      CloseableHttpResponse response = get(client, String.format(ARTIFACTS_URL, buildId));

      String content = EntityUtils.toString(response.getEntity());
      ObjectMapper mapper = MAPPER.get();
      return mapper.readValue(content, Build.class);
    }
  }

  @NotNull
  private CloseableHttpResponse get(@NotNull CloseableHttpClient client, @NotNull String url) throws IOException {
    HttpGet request = new HttpGet(url);
    request.addHeader("Authorization", "Bearer " + myToken);
    request.addHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType());
    return client.execute(request);
  }

}
