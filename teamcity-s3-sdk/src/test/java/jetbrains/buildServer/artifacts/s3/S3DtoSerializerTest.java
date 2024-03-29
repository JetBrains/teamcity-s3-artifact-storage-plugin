package jetbrains.buildServer.artifacts.s3;

import com.fasterxml.jackson.core.JsonProcessingException;
import jetbrains.buildServer.artifacts.s3.serialization.S3XmlSerializerFactory;
import jetbrains.buildServer.artifacts.s3.serialization.XmlSerializer;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;
import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class S3DtoSerializerTest {
  public void test_jdom_serialization() {
    final BucketLocationFetcher.BucketLocationDto actual = new BucketLocationFetcher.BucketLocationDto("bucketName", "bucketLocation");

    XmlSerializer xmlSerializer = S3XmlSerializerFactory.getInstance();
    final Element element = xmlSerializer.serializeAsElement(actual);
    final String jdom = new XMLOutputter().outputString(element);

    final BucketLocationFetcher.BucketLocationDto deserialized = xmlSerializer.deserialize(jdom, BucketLocationFetcher.BucketLocationDto.class);
    Assert.assertEquals(deserialized.getLocation(), actual.getLocation());
    Assert.assertEquals(deserialized.getName(), actual.getName());
  }

  public void test_jackson_serialization() throws JsonProcessingException {
    final BucketLocationFetcher.BucketLocationDto actual = new BucketLocationFetcher.BucketLocationDto("bucketName", "bucketLocation");

    XmlSerializer xmlSerializer = S3XmlSerializerFactory.getInstance();
    final String jackson = xmlSerializer.serialize(actual);

    final BucketLocationFetcher.BucketLocationDto deserialized = xmlSerializer.deserialize(jackson, BucketLocationFetcher.BucketLocationDto.class);
    Assert.assertEquals(deserialized.getLocation(), actual.getLocation());
    Assert.assertEquals(deserialized.getName(), actual.getName());
  }
}
