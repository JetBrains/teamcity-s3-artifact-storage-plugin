package jetbrains.buildServer.artifacts.s3.publish.presigned.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.agent.IOUtil;
import org.testng.annotations.Test;

@Test
public class RepeatableFilePartRequestEntityTest extends BaseTestCase {
  public void testWriteRequest() throws IOException {
    final File file = new File(getClass().getClassLoader().getResource("artifacts/file.zip").getFile());
    final File out = Files.createTempFile("tmp", "zip").toFile();

    try (final FileOutputStream os = new FileOutputStream(out)) {
      final long length = file.length();
      final long part = length / 3;
      for (int i = 0; i < 3; i++) {
        final long contentLength = Math.min(part, file.length() - part * i);
        new RepeatableFilePartRequestEntity(file, i * part, contentLength).writeRequest(os);
      }
    }

    final RepeatableFilePartRequestEntity part2 = new RepeatableFilePartRequestEntity(file, 6, 10);
    final RepeatableFilePartRequestEntity part3 = new RepeatableFilePartRequestEntity(file, 10, file.length());


    assertEquals(IOUtil.readLines(file), IOUtil.readLines(out));
  }
}
