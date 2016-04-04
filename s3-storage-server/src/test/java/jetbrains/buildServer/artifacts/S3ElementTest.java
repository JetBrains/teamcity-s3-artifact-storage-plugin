package jetbrains.buildServer.artifacts;

import jetbrains.buildServer.artifacts.tree.S3ArtifactsListBrowser;
import jetbrains.buildServer.artifacts.tree.S3Element;
import jetbrains.buildServer.util.browser.Element;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * Created by Nikita.Skvortsov
 * date: 08.02.2016.
 */
@Test
public class S3ElementTest {

  private S3ArtifactsListBrowser myS3Browser;

  @BeforeMethod
  public void setUp() throws Exception {
    myS3Browser = new S3ArtifactsListBrowser(new ArrayList<>());
  }

  public void testIsLeaf() throws Exception {
    Element el = new S3Element("path", new S3Artifact("path", null, 0), myS3Browser);
    assertThat(el.isLeaf()).isFalse();

    el = new S3Element("path", new S3Artifact("path", "url", 0), myS3Browser);
    assertThat(el.isLeaf()).isTrue();
  }

  public void testPathAndName() throws Exception {
    Element el = new S3Element("f.txt", new S3Artifact("f.txt", null, 0), myS3Browser);

    assertThat(el.getName()).isEqualTo("f.txt");
    assertThat(el.getFullName()).isEqualTo("f.txt");

    el = new S3Element("some/path/file.txt", new S3Artifact("some/path/file.txt", null, 0), myS3Browser);

    assertThat(el.getName()).isEqualTo("file.txt");
    assertThat(el.getFullName()).isEqualTo("some/path/file.txt");
  }

  @Test(expectedExceptions = IllegalStateException.class)
  public void testIllegalThrowForInputStream() throws Exception {
    Element el = new S3Element("f.txt", new S3Artifact("f.txt", "url", 0), myS3Browser);
    assertThat(el.getSize()).isEqualTo(0);

    el.getInputStream();
  }
}
