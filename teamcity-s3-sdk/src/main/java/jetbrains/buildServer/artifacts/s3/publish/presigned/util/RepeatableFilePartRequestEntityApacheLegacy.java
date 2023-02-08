package jetbrains.buildServer.artifacts.s3.publish.presigned.util;

import java.io.IOException;
import java.io.OutputStream;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.jetbrains.annotations.NotNull;

public class RepeatableFilePartRequestEntityApacheLegacy implements RequestEntity {
  @NotNull
  private final FilePart myFilePart;
  @NotNull private final String myContentType;

  public RepeatableFilePartRequestEntityApacheLegacy(@NotNull FilePart filePart, @NotNull String contentType) {
    myFilePart = filePart;
    myContentType = contentType;
  }

  @NotNull
  @Override
  public String getContentType() {
    return myContentType;
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
  public void writeRequest(OutputStream outputStream) throws IOException {
    myFilePart.write(outputStream);
  }
}
