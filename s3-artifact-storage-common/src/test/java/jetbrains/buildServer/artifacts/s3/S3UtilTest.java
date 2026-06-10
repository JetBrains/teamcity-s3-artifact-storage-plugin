

package jetbrains.buildServer.artifacts.s3;

import java.io.File;
import jetbrains.buildServer.util.TestFor;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;

@Test
public class S3UtilTest {
  @DataProvider
  public Object[][] getContentTypeData() {
    return new Object[][]{
      {"file.zip", "application/zip"},
      {"file.txt", "text/plain"},
      {"file.jpg", "image/jpeg"},
      {"file.png", "image/png"},
      {"file.bin", "application/octet-stream"},
      {"file.htm", "text/html"},
      {"file.html", "text/html"},
      {"file.css", "text/css"},
      {"file.js", "application/javascript"},
    };
  }

  @Test(dataProvider = "getContentTypeData")
  public void getContentTypeTest(String fileName, String expectedType) {
    Assert.assertEquals(S3Util.getContentType(new File("S3UtilsTest", fileName)), expectedType);
  }

  @Test
  @TestFor(issues = "TW-101373")
  public void test_getPathPrefix() {
    then(S3ArtifactUtil.getPathPrefix(null, "x", "y", 1)).isEqualTo("x/y/1/");
    then(S3ArtifactUtil.getPathPrefix(" ", "x", "y", 1)).isEqualTo("x/y/1/");

    then(S3ArtifactUtil.getPathPrefix("a/", "x", "y", 1)).isEqualTo("a/x/y/1/");
    then(S3ArtifactUtil.getPathPrefix("/a/", "x", "y", 1)).isEqualTo("a/x/y/1/");
  }
}
