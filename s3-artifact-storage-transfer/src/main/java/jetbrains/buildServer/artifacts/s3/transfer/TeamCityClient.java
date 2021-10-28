package jetbrains.buildServer.artifacts.s3.transfer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import jetbrains.buildServer.artifacts.s3.transfer.model.*;
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
    "http://localhost:8111/bs/app/rest/projects/%s/?fields=id,buildTypes(buildType(id,name,projectId)),projectFeatures(projectFeature(id,type,properties(property(name,value)))),projects(project(id))";
  public static final String PROPERTIES_URL = "http://localhost:8111/bs/app/rest/projects/%s/projectFeatures/%s/properties/";
  public static final String BUILDS_URL = "http://localhost:8111/bs/app/rest/builds/?locator=count:-1,project:%s";
  public static final String ARTIFACTS_URL = "http://localhost:8111/bs/app/rest/builds/%s/?fields=id,buildType,project,artifacts(file(name,fullName)),artifactsDirectory";

  @NotNull
  private static final ThreadLocal<ObjectMapper> MAPPER = ThreadLocal.withInitial(() -> new ObjectMapper().registerModule(new JaxbAnnotationModule())
                                                                                                          .configure(SerializationFeature.WRAP_ROOT_VALUE, false)
                                                                                                          .configure(SerializationFeature.INDENT_OUTPUT, false)
                                                                                                          .configure(MapperFeature.USE_ANNOTATIONS, true)
                                                                                                          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
  );

  private final String myToken;

  public TeamCityClient(String token) {
    myToken = token;
  }

  @NotNull
  public Project getDetails(String project) throws IOException {
    try (CloseableHttpClient client = HttpClients.createDefault()) {

      CloseableHttpResponse response = get(client, String.format(DETAILS_URL, project));

      String content = EntityUtils.toString(response.getEntity());
      ObjectMapper mapper = MAPPER.get();

      return mapper.readValue(content, Project.class);
    }
  }

  @NotNull
  public List<Property> getFeatureProperties(String project, String featureLocator) throws IOException {
    try (CloseableHttpClient client = HttpClients.createDefault()) {

      CloseableHttpResponse response = get(client, String.format(PROPERTIES_URL, project, featureLocator));

      String content = EntityUtils.toString(response.getEntity());
      ObjectMapper mapper = MAPPER.get();
      Properties properties = mapper.readValue(content, Properties.class);

      return properties.getProperty() != null ? properties.getProperty() : Collections.emptyList();
    }
  }

  @NotNull
  public List<Build> getBuilds(String project) throws IOException {
    try (CloseableHttpClient client = HttpClients.createDefault()) {

      CloseableHttpResponse response = get(client, String.format(BUILDS_URL, project));

      String content = EntityUtils.toString(response.getEntity());
      ObjectMapper mapper = MAPPER.get();
      Builds result = mapper.readValue(content, Builds.class);

      return result.getBuild() != null ? result.getBuild() : Collections.emptyList();
    }
  }

  @NotNull
  public BuildArtifacts getArtifacts(String buildId) throws IOException {
    try (CloseableHttpClient client = HttpClients.createDefault()) {

      CloseableHttpResponse response = get(client, String.format(ARTIFACTS_URL, buildId));

      String content = EntityUtils.toString(response.getEntity());
      ObjectMapper mapper = MAPPER.get();
      Build result = mapper.readValue(content, Build.class);

      List<String> files = result.getArtifacts().getFile().stream().map(File::getName).collect(Collectors.toList());

      return new BuildArtifacts(result, files);
    }
  }

  private CloseableHttpResponse get(CloseableHttpClient client, String format) throws IOException {
    HttpGet request = new HttpGet(format);
    request.addHeader("Authorization", "Bearer " + myToken);
    request.addHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType());
    return client.execute(request);
  }

}
