package jetbrains.buildServer.artifacts.s3.web;

import jetbrains.buildServer.artifacts.s3.preSignedUrl.S3PreSignedUrlProvider;
import jetbrains.buildServer.serverSide.ServerPaths;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Set;

import static jetbrains.buildServer.artifacts.s3.web.S3ArtifactDownloadProcessor.EXTENSION_EXCLUSION_LIST_ELEMENT;
import static jetbrains.buildServer.artifacts.s3.web.S3ArtifactDownloadProcessor.S3_ELEMENT;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;


public class S3ArtifactDownloadProcessorTest {

  private static S3ArtifactDownloadProcessor downloadProcessor;

  @BeforeClass
  public void beforeClass() {

    S3PreSignedUrlProvider preSignedUrlProvider = mock(S3PreSignedUrlProvider.class);
    ServerPaths serverPaths = mock(ServerPaths.class);
    downloadProcessor = new S3ArtifactDownloadProcessor(preSignedUrlProvider, serverPaths);
  }

  @Test
  public void testReadFrom() {
    Element root = new Element("root");
    Element s3Element = new Element(S3_ELEMENT);
    Element extensionExclusionListElement = new Element(EXTENSION_EXCLUSION_LIST_ELEMENT);
    extensionExclusionListElement.setText("jpg,png");
    s3Element.addContent(extensionExclusionListElement);
    root.addContent(s3Element);

    downloadProcessor.readFrom(root);
    Set<String> extendedFileExclusions = downloadProcessor.getExcludedFileExtensions();

    assertEquals(extendedFileExclusions.size(), 2);
    assertTrue(extendedFileExclusions.contains("jpg"));
    assertTrue(extendedFileExclusions.contains("png"));
  }

  @Test
  public void testWriteTo() {
    Element root = new Element("root");
    Element s3Element = new Element(S3_ELEMENT);
    Element extensionExclusionListElement = new Element(EXTENSION_EXCLUSION_LIST_ELEMENT);
    extensionExclusionListElement.setText("jpg,png");
    s3Element.addContent(extensionExclusionListElement);
    root.addContent(s3Element);

    downloadProcessor.readFrom(root);

    Element writeToRoot = new Element("root");
    downloadProcessor.writeTo(writeToRoot);

    Document doc = new Document(writeToRoot);
    XMLOutputter xmlOutputter = new XMLOutputter();
    String xml = xmlOutputter.outputString(doc);

    String expectedXml = "<s3><extensionExclusionList>jpg,png</extensionExclusionList></s3>";
    assertTrue(xml.contains(expectedXml));
  }
}
