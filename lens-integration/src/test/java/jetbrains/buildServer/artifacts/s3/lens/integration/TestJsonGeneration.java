package jetbrains.buildServer.artifacts.s3.lens.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jetbrains.buildServer.artifacts.s3.lens.integration.dto.UploadFileEvent;
import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class TestJsonGeneration {
  private static final String TEST_JSON_STRING =
    "{\"successful\":false,\"objectKey\":\"file.zip\",\"fileSize\":123456789,\"numberOfParts\":1,\"chunkSize\":123456789,\"duration\":123456789,\"restartCount\":0}";

  @Test
  public void testDtoToJson() throws JsonProcessingException {
    UploadFileEvent event = new UploadFileEvent();
    event.setObjectKey("file.zip");
    event.setFileSize(123456789L);
    event.setDuration(123456789L);
    event.setNumberOfParts(1);
    event.setChunkSize(123456789L);
    event.setRestartCount(0);

    ObjectMapper mapper = new ObjectMapper();
    String json = mapper.writeValueAsString(event);
    System.out.println(json);
    Assert.assertEquals(json, TEST_JSON_STRING);
  }
}
