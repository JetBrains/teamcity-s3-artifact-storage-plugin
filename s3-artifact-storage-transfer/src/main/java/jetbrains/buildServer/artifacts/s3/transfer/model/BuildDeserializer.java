package jetbrains.buildServer.artifacts.s3.transfer.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.io.IOException;
import java.util.ArrayList;

public class BuildDeserializer extends StdDeserializer<Build> {

  public BuildDeserializer() {
    super(Build.class);
  }

  @Override
  public Build deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    JsonNode node = p.getCodec().readTree(p);
    String id = node.get("id").asText();
    String directory = node.get("artifactsDirectory").asText();
    ArrayNode filesNode = (ArrayNode)node.get("artifacts").get("file");
    ArrayList<String> artifacts = new ArrayList<>();
    for (JsonNode jsonNode : filesNode) {
      artifacts.add(jsonNode.get("name").asText());
    }

    JsonNode buildType = node.get("buildType");
    String projectId = buildType.get("projectId").asText();
    String buildTypeId = buildType.get("id").asText();
    String buildTypeName = buildType.get("name").asText();

    return new Build(id, projectId, buildTypeId, buildTypeName, directory, artifacts);
  }
}
