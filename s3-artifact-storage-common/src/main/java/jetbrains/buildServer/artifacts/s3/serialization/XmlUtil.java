package jetbrains.buildServer.artifacts.s3.serialization;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import java.io.IOException;
import java.io.StringWriter;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Text;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jetbrains.annotations.NotNull;

public class XmlUtil {
  @NotNull
  private static final Logger LOGGER = Logger.getInstance(XmlUtil.class.getName());
  @NotNull
  private static final XMLOutputter OUR_XML_OUTPUTTER = outputter();

  @NotNull
  public static String writeDocumentVerbatim(@NotNull final Element element) {
    final Document document = new Document(element);
    try (final StringWriter writer = new StringWriter()) {
      OUR_XML_OUTPUTTER.output(document, writer);
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

  @NotNull
  private static XMLOutputter outputter() {
    final JDOMUtil.MyXMLOutputter xmlOutputter = new JDOMUtil.MyXMLOutputter();
    final Format format = Format.getCompactFormat()
                                .setIndent("  ")
                                .setTextMode(Format.TextMode.PRESERVE)
                                .setEncoding("UTF-8")
                                .setOmitEncoding(false)
                                .setOmitDeclaration(false)
                                .setLineSeparator(System.getProperty("line.separator"));
    xmlOutputter.setFormat(format);
    return xmlOutputter;
  }
}
