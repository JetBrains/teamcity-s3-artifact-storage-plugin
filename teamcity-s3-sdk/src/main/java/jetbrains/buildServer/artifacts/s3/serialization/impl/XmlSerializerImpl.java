package jetbrains.buildServer.artifacts.s3.serialization.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
import java.io.IOException;
import jetbrains.buildServer.artifacts.s3.serialization.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public class XmlSerializerImpl implements XmlSerializer {
  @NotNull
  private static final ThreadLocal<ObjectMapper> OUR_XML_MAPPER = ThreadLocal.withInitial(() ->
                                                                                            new XmlMapper()
                                                                                              .setDefaultUseWrapper(false)
                                                                                              .registerModule(new JaxbAnnotationModule())
                                                                                              .configure(SerializationFeature.WRAP_ROOT_VALUE, false)
                                                                                              .configure(SerializationFeature.INDENT_OUTPUT, false)
                                                                                              .configure(MapperFeature.USE_ANNOTATIONS, true));

  @Override
  public String serialize(@NotNull final Object object) {
    try {
      return OUR_XML_MAPPER.get().writeValueAsString(object);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Element serializeAsElement(@NotNull Object object) {
    final String xml = serialize(object);
    return new JDomElementDeserializer().deserialize(xml);
  }

  @NotNull
  public <T> T deserialize(@NotNull final String value, @NotNull final Class<T> clazz) {
    try {
      return OUR_XML_MAPPER.get().readValue(value, clazz);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
