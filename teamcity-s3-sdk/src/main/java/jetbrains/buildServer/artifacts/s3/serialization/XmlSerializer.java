package jetbrains.buildServer.artifacts.s3.serialization;


import com.fasterxml.jackson.core.JsonProcessingException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public interface XmlSerializer {

  String serialize(@NotNull Object object) throws JsonProcessingException;

  Element serializeAsElement(@NotNull Object object);

  <T> T deserialize(@NotNull String xml, @NotNull Class<T> clazz);
}
