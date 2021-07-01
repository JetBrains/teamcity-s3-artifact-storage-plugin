package jetbrains.buildServer.artifacts.s3.serialization.impl;

import java.io.IOException;
import java.io.StringReader;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.NotNull;

public class JDomElementDeserializer {
  @NotNull
  public Element deserialize(@NotNull final String xml) {
    final StringReader stringReader = new StringReader(xml);
    try {
      return new SAXBuilder().build(stringReader).detachRootElement();
    } catch (JDOMException | IOException e) {
      throw new RuntimeException(e);
    }
  }
}
