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
  @NotNull
  private static final ThreadLocal<ObjectMapper> MAPPER = ThreadLocal.withInitial(() -> new ObjectMapper().registerModule(new JaxbAnnotationModule())
                                                                                                          .configure(SerializationFeature.WRAP_ROOT_VALUE, false)
                                                                                                          .configure(SerializationFeature.INDENT_OUTPUT, false)
                                                                                                          .configure(MapperFeature.USE_ANNOTATIONS, true)
                                                                                                          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
  );
  private Parameters myParameters;

  public TeamCityClient(Parameters parameters) {
    myParameters = parameters;
  }

  @NotNull
  public Project getDetails(String project) throws IOException {
    try (CloseableHttpClient client = HttpClients.createDefault()) {

      HttpGet request = new HttpGet("http://localhost:8111/bs/app/rest/projects/" + project +
                                    "/?fields=id,buildTypes(buildType(id,name,projectId)),projectFeatures(projectFeature(id,type,properties(property(name,value)))),projects(project(id))");
      request.addHeader("Authorization", "Bearer " + myParameters.getToken());
      request.addHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType());
      CloseableHttpResponse response = client.execute(request);

      String content = EntityUtils.toString(response.getEntity());
      ObjectMapper mapper = MAPPER.get();

      return mapper.readValue(content, Project.class);
    }
  }

  @NotNull
  public List<Property> getFeatureProperties(String project, String featureLocator) throws IOException {
    try (CloseableHttpClient client = HttpClients.createDefault()) {

      HttpGet request = new HttpGet("http://localhost:8111/bs/app/rest/projects/" + project + "/projectFeatures/" + featureLocator + "/properties/");
      request.addHeader("Authorization", "Bearer " + myParameters.getToken());
      request.addHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType());
      CloseableHttpResponse response = client.execute(request);

      String content = EntityUtils.toString(response.getEntity());
      ObjectMapper mapper = MAPPER.get();
      Properties properties = mapper.readValue(content, Properties.class);

      return properties.getProperty() != null ? properties.getProperty() : Collections.emptyList();
    }
  }

  @NotNull
  public List<Build> getBuilds(String project) throws IOException {
    try (CloseableHttpClient client = HttpClients.createDefault()) {

      HttpGet request = new HttpGet("http://localhost:8111/bs/app/rest/builds/?locator=count:-1,project:" + project);
      request.addHeader("Authorization", "Bearer " + myParameters.getToken());
      request.addHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType());
      CloseableHttpResponse response = client.execute(request);

      String content = EntityUtils.toString(response.getEntity());
      ObjectMapper mapper = MAPPER.get();
      Builds result = mapper.readValue(content, Builds.class);

      return result.getBuild() != null ? result.getBuild() : Collections.emptyList();
    }
  }

  @NotNull
  public BuildArtifacts getArtifacts(String buildId) throws IOException {
    try (CloseableHttpClient client = HttpClients.createDefault()) {

      HttpGet request = new HttpGet("http://localhost:8111/bs/app/rest/builds/" + buildId + "/?fields=id,buildType,project,artifacts(file(name,fullName)),artifactsDirectory");
      request.addHeader("Authorization", "Bearer " + myParameters.getToken());
      request.addHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType());
      CloseableHttpResponse response = client.execute(request);

      String content = EntityUtils.toString(response.getEntity());
      ObjectMapper mapper = MAPPER.get();
      Build result = mapper.readValue(content, Build.class);

      List<String> files = result.getArtifacts().getFile().stream().map(File::getName).collect(Collectors.toList());

      return new BuildArtifacts(result, files);
    }
  }


}
