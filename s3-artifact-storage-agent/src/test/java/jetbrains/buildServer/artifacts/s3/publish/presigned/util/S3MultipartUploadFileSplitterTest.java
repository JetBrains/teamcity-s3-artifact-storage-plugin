package jetbrains.buildServer.artifacts.s3.publish.presigned.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.agent.IOUtil;
import org.testng.annotations.Test;

@Test
public class S3MultipartUploadFileSplitterTest extends BaseTestCase {
  public void splitFileWithDigests() throws IOException {
    final File file = new File(getClass().getClassLoader().getResource("artifacts/file.zip").getFile());
    final File out = Files.createTempFile("tmp", "zip").toFile();

    try (final FileOutputStream os = new FileOutputStream(out)) {
      final long length = file.length();

      final S3MultipartUploadFileSplitter splitter = new S3MultipartUploadFileSplitter(length / 3);
      for (FilePart p : splitter.getFileParts(file, 3, true)) {
        os.write(splitter.read(file, p));
      }
    }

    assertEquals(IOUtil.readLines(file), IOUtil.readLines(out));
  }


  public void splitFileWithoutDigests() throws IOException {
    final File file = new File(getClass().getClassLoader().getResource("artifacts/file.zip").getFile());
    final File out = Files.createTempFile("tmp", "zip").toFile();

    try (final FileOutputStream os = new FileOutputStream(out)) {
      final long length = file.length();

      final S3MultipartUploadFileSplitter splitter = new S3MultipartUploadFileSplitter(length / 3);
      for (FilePart p : splitter.getFileParts(file, 3, false)) {
        os.write(splitter.read(file, p));
      }
    }

    assertEquals(IOUtil.readLines(file), IOUtil.readLines(out));
  }
}
