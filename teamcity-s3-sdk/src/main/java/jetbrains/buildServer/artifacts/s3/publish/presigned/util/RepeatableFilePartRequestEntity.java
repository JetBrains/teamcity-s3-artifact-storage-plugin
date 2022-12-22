package jetbrains.buildServer.artifacts.s3.publish.presigned.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import org.apache.http.entity.AbstractHttpEntity;
import org.jetbrains.annotations.NotNull;

public class RepeatableFilePartRequestEntity extends AbstractHttpEntity {
  @NotNull
  private final FilePart myFilePart;

  public RepeatableFilePartRequestEntity(@NotNull FilePart filePart, @NotNull String contentType) {
    myFilePart = filePart;
    setContentType(contentType);
  }

  @Override
  public boolean isRepeatable() {
    return true;
  }

  @Override
  public long getContentLength() {
    return myFilePart.getLength();
  }

  @Override
  public InputStream getContent() throws IOException, UnsupportedOperationException {
    throw new UnsupportedEncodingException("File part request doet not allow to get stream to content");
  }

  @Override
  public void writeTo(OutputStream outputStream) throws IOException {
    myFilePart.write(outputStream);
  }

  @Override
  public boolean isStreaming() {
    return false;
  }
}
