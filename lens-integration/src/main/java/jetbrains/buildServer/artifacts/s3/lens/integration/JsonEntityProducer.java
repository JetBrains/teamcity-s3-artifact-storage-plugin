package jetbrains.buildServer.artifacts.s3.lens.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import jetbrains.buildServer.util.http.EntityProducer;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;

class JsonEntityProducer implements EntityProducer {
  private final static ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private final String myJson;

  public JsonEntityProducer(Object event) throws JsonProcessingException {
    myJson = OBJECT_MAPPER.writeValueAsString(event);
  }

  @Override
  public HttpEntity entity4() {
    return new StringEntity(myJson, ContentType.APPLICATION_JSON);
  }

  @Override
  public RequestEntity entity3() {
    try {
      return new StringRequestEntity(myJson, ContentType.APPLICATION_JSON.getMimeType(), StandardCharsets.UTF_8.name());
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }
}
