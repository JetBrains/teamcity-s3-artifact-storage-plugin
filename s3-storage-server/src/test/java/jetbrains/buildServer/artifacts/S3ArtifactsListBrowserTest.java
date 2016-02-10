package jetbrains.buildServer.artifacts;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Created by Nikita.Skvortsov
 * date: 08.02.2016.
 */
@Test
public class S3ArtifactsListBrowserTest {

  private Map<String, String> myMap;

  @BeforeMethod
  public void setUp() throws Exception {
    myMap = new HashMap<>();
  }

  public void testSingleFile() throws Exception {
    myMap.put("file.txt", "http://fake.url.nowhere");
    final S3ArtifactsListBrowser browser = new S3ArtifactsListBrowser(myMap);

    assertThat(browser.getRoot().getChildren())
        .extracting("name", "fullName", "leaf")
        .containsExactly(tuple("file.txt", "file.txt", true));

  }

  public void testSinglePath() throws Exception {
    myMap.put("some/path/file.txt", "http://fake.url.nowhere");
    final S3ArtifactsListBrowser browser = new S3ArtifactsListBrowser(myMap);

    assertThat(browser.getChildren("")).extracting("name", "fullName", "leaf").containsOnly(tuple("some", "some", false));
    assertThat(browser.getChildren("some")).extracting("name", "fullName", "leaf").containsOnly(tuple("path", "some/path", false));
    assertThat(browser.getChildren("some/path")).extracting("name", "fullName", "leaf").containsOnly(tuple("file.txt", "some/path/file.txt", true));
  }

  public void testPartialPathPrefix() throws Exception {
    myMap.put("some/path/file.txt", "http://fake.url.nowhere");
    final S3ArtifactsListBrowser browser = new S3ArtifactsListBrowser(myMap);

    assertThat(browser.getElement("som")).isNull();
    assertThat(browser.getChildren("som")).isEmpty();
    assertThat(browser.getElement("some/pa")).isNull();
    assertThat(browser.getChildren("some/pa")).isEmpty();
    assertThat(browser.getElement("some/path/f")).isNull();
    assertThat(browser.getChildren("some/path/f")).isEmpty();
  }

  public void testMultiplePaths() throws Exception {
    myMap.put("some/path/file1.txt", "http://fake.url.nowhere");
    myMap.put("some/path/file2.txt", "http://fake.url.nowhere");
    myMap.put("some/file3.txt", "http://fake.url.nowhere");
    myMap.put("file4.txt", "http://fake.url.nowhere");

    final S3ArtifactsListBrowser browser = new S3ArtifactsListBrowser(myMap);

    assertThat(browser.getRoot().getChildren()).extracting("name").containsOnly("some", "file4.txt");
    assertThat(browser.getChildren("some")).extracting("fullName").containsOnly("some/path", "some/file3.txt");
    assertThat(browser.getChildren("some/path")).extracting("name").containsOnly("file1.txt", "file2.txt");
  }
}
