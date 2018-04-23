package jetbrains.buildServer.artifacts.s3.web;

import com.amazonaws.services.s3.AmazonS3;
import java.util.Map;
import jetbrains.buildServer.artifacts.s3.S3Util;
import org.jdom.Content;

public abstract class S3ClientResourceHandler implements ResourceHandler {
  @Override
  public Content getContent(final Map<String, String> parameters) throws Exception {
    return S3Util.withS3Client(parameters, s3Client -> {
      return getContent(s3Client, parameters);
    });
  }

  protected abstract Content getContent(final AmazonS3 s3Client, final Map<String, String> parameters) throws Exception;
}
