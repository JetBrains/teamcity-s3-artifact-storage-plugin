package jetbrains.buildServer.artifacts.s3.serialization;

import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.io.StringWriter;
import org.jdom.Element;
import org.jdom.Text;
import org.jetbrains.annotations.NotNull;

public class XmlUtil {
  @NotNull
  private static final Logger LOGGER = Logger.getInstance(XmlUtil.class.getName());

  @NotNull
  public static String writeDocumentVerbatim(@NotNull final Element element) {
    try (final StringWriter writer = new StringWriter()) {
      jetbrains.buildServer.util.XmlUtil.saveElement(element, writer);
      return writer.toString();
    } catch (final IOException e) {
      LOGGER.warnAndDebugDetails("Got exception while closing XML Document", e);
      throw new RuntimeException(e);
    }
  }

  @NotNull
  public static Element createElementVerbatim(@NotNull final String name, @NotNull final String text) {
    return addContentVerbatim(new Element(name), text);
  }

  @NotNull
  private static Element addContentVerbatim(@NotNull final Element element, @NotNull final String text) {
    return element.addContent(new Text(text));
  }
}
