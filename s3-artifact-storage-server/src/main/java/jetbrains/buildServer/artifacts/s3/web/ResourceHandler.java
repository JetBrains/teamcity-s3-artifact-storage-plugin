package jetbrains.buildServer.artifacts.s3.web;

import java.util.Map;
import org.jdom.Content;

public interface ResourceHandler {
  public Content getContent(Map<String, String> parameters) throws Exception;
}
