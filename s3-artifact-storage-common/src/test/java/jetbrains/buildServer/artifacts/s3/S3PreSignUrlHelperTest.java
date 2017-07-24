package jetbrains.buildServer.artifacts.s3;

import jetbrains.buildServer.BaseTestCase;
import org.testng.annotations.Test;

import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Created by Evgeniy Koshkin (evgeniy.koshkin@jetbrains.com) on 24.07.17.
 */
@Test
public class S3PreSignUrlHelperTest extends BaseTestCase {
  @Test
  public void testPreSignUrlMapping() throws Exception {
    Map<String, URL> data = new HashMap<String, URL>();
    data.put("some key", new URL("http://some url"));
    data.put("another key", new URL("http://another url"));
    String writtenData = S3PreSignUrlHelper.writePreSignUrlMapping(data);
    assertFalse(writtenData.isEmpty());
    Map<String, URL> readData = S3PreSignUrlHelper.readPreSignUrlMapping(writtenData);
    assertEquals(data, readData);
  }

  @Test
  public void testS3ObjectKeys() throws Exception {
    Collection<String> data = new HashSet<String>();
    data.add("one key");
    data.add("other key");
    String writtenData = S3PreSignUrlHelper.writeS3ObjectKeys(data);
    assertFalse(writtenData.isEmpty());
    Collection<String> readData = S3PreSignUrlHelper.readS3ObjectKeys(writtenData);
    assertEquals(data, readData);
  }
}
