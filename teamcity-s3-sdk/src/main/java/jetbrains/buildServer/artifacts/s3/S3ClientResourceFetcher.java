

package jetbrains.buildServer.artifacts.s3;

import java.util.Map;
import jetbrains.buildServer.artifacts.s3.serialization.S3XmlSerializerFactory;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public abstract class S3ClientResourceFetcher<T> {

  @NotNull
  public String fetchAsXml(final Map<String, String> parameters, @NotNull final String projectId) throws Exception {
    final T dto = fetchDto(parameters, projectId);
    return S3XmlSerializerFactory.getInstance().serialize(dto);
  }

  @NotNull
  public Element fetchAsElement(final Map<String, String> parameters, @NotNull final String projectId) throws Exception {
    final T dto = fetchDto(parameters, projectId);
    return S3XmlSerializerFactory.getInstance().serializeAsElement(dto);
  }

  public Element fetchCurrentValueAsElement(final Map<String, String> parameters, @NotNull final String projectId) throws Exception{
    final T dto = fetchCurrentValue(parameters, projectId);
    return S3XmlSerializerFactory.getInstance().serializeAsElement(dto);
  }

  protected abstract T fetchCurrentValue(final Map<String, String> parameters, @NotNull final String projectId) throws Exception;

  protected abstract T fetchDto(final Map<String, String> parameters, @NotNull final String projectId) throws Exception;
}
