package jetbrains.buildServer.artifacts.s3.transfer.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ProjectDeserializer extends StdDeserializer<Project> {
  public ProjectDeserializer() {
    super(Project.class);
  }

  @Override
  public Project deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    JsonNode node = p.getCodec().readTree(p);
    String id = node.get("id").asText();
    String name = node.get("name").asText();
    ArrayNode featuresNode = ((ArrayNode)node.get("projectFeatures").get("projectFeature"));

    List<Feature> features = new ArrayList<>();
    for (JsonNode featureNode : featuresNode) {
      String featureId = featureNode.get("id").asText();
      String featureType = featureNode.get("type").asText();
      HashMap<String, String> propertyMap = new HashMap<>();
      for (JsonNode propertyNode : featureNode.get("properties").get("property")) {
        JsonNode nameNode = propertyNode.get("name");
        JsonNode valueNode = propertyNode.get("value");
        if (nameNode != null && valueNode != null) {
          propertyMap.put(nameNode.asText(), valueNode.asText());
        }
      }
      features.add(new Feature(featureId, featureType, propertyMap));
    }

    ArrayNode subprojectsNode = ((ArrayNode)node.get("projects").get("project"));
    ArrayList<String> subprojects = new ArrayList<>();
    for (JsonNode jsonNode : subprojectsNode) {
      subprojects.add(jsonNode.get("id").asText());
    }

    return new Project(id, name, features, subprojects);
  }
}
