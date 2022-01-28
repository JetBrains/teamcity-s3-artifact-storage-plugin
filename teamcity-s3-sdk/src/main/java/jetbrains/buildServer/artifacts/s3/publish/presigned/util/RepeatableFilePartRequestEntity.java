package jetbrains.buildServer.artifacts.s3.publish.presigned.util;

import java.io.IOException;
import java.io.OutputStream;
import jetbrains.buildServer.artifacts.s3.S3Util;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.jetbrains.annotations.NotNull;

public class RepeatableFilePartRequestEntity implements RequestEntity {
  @NotNull
  private final FilePart myFilePart;

  public RepeatableFilePartRequestEntity(@NotNull FilePart filePart) {
    myFilePart = filePart;
  }

  @Override
  public boolean isRepeatable() {
    return true;
  }

  @Override
  public void writeRequest(@NotNull final OutputStream out) throws IOException {
    myFilePart.write(out);
  }

  @Override
  public long getContentLength() {
    return myFilePart.getLength();
  }

  @NotNull
  @Override
  public String getContentType() {
    return S3Util.getContentType(myFilePart.getFile());
  }

}
