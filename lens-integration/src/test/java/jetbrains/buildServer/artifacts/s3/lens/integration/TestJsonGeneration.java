package jetbrains.buildServer.artifacts.s3.lens.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jetbrains.buildServer.artifacts.s3.lens.integration.dto.UploadFileEvent;
import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class TestJsonGeneration {
  private static final String TEST_JSON_STRING =
    "{\"build.artifacts.object.upload.result\":\"successful\",\"build.artifacts.object.key\":\"file.zip\",\"build.artifacts.object.size\":123456789,\"build.artifacts.object.chunk_count\":1,\"build.artifacts.object.chunk_size\":123456789,\"build.artifacts.object.upload.duration\":123456789,\"build.artifacts.object.upload_retry_count\":0}";

  @Test
  public void testDtoToJson() throws JsonProcessingException {
    UploadFileEvent event = new UploadFileEvent();
    event.setObjectKey("file.zip");
    event.setFileSize(123456789L);
    event.setDuration(123456789L);
    event.setNumberOfParts(1);
    event.setChunkSize(123456789L);
    event.setRestartCount(0);
    event.setUploadResult("successful");

    ObjectMapper mapper = new ObjectMapper();
    String json = mapper.writeValueAsString(event);
    System.out.println(json);
    Assert.assertEquals(json, TEST_JSON_STRING);
  }
}
