package jetbrains.buildServer.artifacts.s3.serialization;

import jetbrains.buildServer.BaseTestCase;
import org.jdom.Element;
import org.testng.annotations.Test;

@Test
public class XmlUtilTest extends BaseTestCase {
  public void testDoubleSpaces() {
    final String doubleSpace = "a  b";
    final Element e = new Element("keys");
    e.addContent(doubleSpace);
    final String s = XmlUtil.writeDocumentVerbatim(e);
    assertContains(s, doubleSpace);
  }
}
