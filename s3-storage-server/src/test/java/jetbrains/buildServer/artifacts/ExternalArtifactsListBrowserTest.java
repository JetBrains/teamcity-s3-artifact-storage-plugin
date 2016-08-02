package jetbrains.buildServer.artifacts;

import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.tree.ExternalArtifactsListBrowser;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Created by Nikita.Skvortsov
 * date: 08.02.2016.
 */
@Test
public class ExternalArtifactsListBrowserTest {

  private List<ExternalArtifact> myList;

  @BeforeMethod
  public void setUp() throws Exception {
    myList = new ArrayList<>();
  }

  public void testSingleFile() throws Exception {
    myList.add(new ExternalArtifact("http://fake.url.nowhere", "file.txt", 1, S3Constants.S3_KEY, "emptyKey"));
    final ExternalArtifactsListBrowser browser = new ExternalArtifactsListBrowser(myList);

    assertThat(browser.getRoot().getChildren())
        .extracting("name", "fullName", "leaf")
        .containsExactly(tuple("file.txt", "file.txt", true));

  }

  public void testSinglePath() throws Exception {
    myList.add(new ExternalArtifact("http://fake.url.nowhere", "some/path/file.txt", 1, S3Constants.S3_KEY, "emptyKey"));
    final ExternalArtifactsListBrowser browser = new ExternalArtifactsListBrowser(myList);

    assertThat(browser.getChildren("")).extracting("name", "fullName", "leaf").containsOnly(tuple("some", "some", false));
    assertThat(browser.getChildren("some")).extracting("name", "fullName", "leaf").containsOnly(tuple("path", "some/path", false));
    assertThat(browser.getChildren("some/path")).extracting("name", "fullName", "leaf").containsOnly(tuple("file.txt", "some/path/file.txt", true));
  }

  public void testPartialPathPrefix() throws Exception {
    myList.add(new ExternalArtifact("http://fake.url.nowhere", "some/path/file.txt", 1, S3Constants.S3_KEY, "emptyKey"));
    final ExternalArtifactsListBrowser browser = new ExternalArtifactsListBrowser(myList);

    assertThat(browser.getElement("som")).isNull();
    assertThat(browser.getChildren("som")).isEmpty();
    assertThat(browser.getElement("some/pa")).isNull();
    assertThat(browser.getChildren("some/pa")).isEmpty();
    assertThat(browser.getElement("some/path/f")).isNull();
    assertThat(browser.getChildren("some/path/f")).isEmpty();
  }

  public void testMultiplePaths() throws Exception {
    myList.add(new ExternalArtifact("http://fake.url.nowhere", "some/path/file1.txt", 1, S3Constants.S3_KEY, "emptyKey"));
    myList.add(new ExternalArtifact("http://fake.url.nowhere", "some/path/file2.txt", 1, S3Constants.S3_KEY, "emptyKey"));
    myList.add(new ExternalArtifact("http://fake.url.nowhere", "some/file3.txt", 1, S3Constants.S3_KEY, "emptyKey"));
    myList.add(new ExternalArtifact("http://fake.url.nowhere", "file4.txt", 1, S3Constants.S3_KEY, "emptyKey"));

    final ExternalArtifactsListBrowser browser = new ExternalArtifactsListBrowser(myList);

    assertThat(browser.getRoot().getChildren()).extracting("name").containsOnly("some", "file4.txt");
    assertThat(browser.getChildren("some")).extracting("fullName").containsOnly("some/path", "some/file3.txt");
    assertThat(browser.getChildren("some/path")).extracting("name").containsOnly("file1.txt", "file2.txt");
  }
}
