package jetbrains.buildServer.artifacts.s3.web;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.Bucket;
import java.util.Map;
import jetbrains.buildServer.artifacts.s3.S3Util;
import org.jdom.Content;
import org.jdom.Element;

/**
 * Gets a list of buckets in S3 storage.
 */
public class BucketsResourceHandler extends S3ClientResourceHandler {
  @Override
  public Content getContent(final AmazonS3 s3Client, final Map<String, String> parameters) {
    S3Util.validateParameters(parameters);
    final Element bucketsElement = new Element("buckets");
    for (Bucket bucket : s3Client.listBuckets()) {
      final Element bucketElement = new Element("bucket");
      final String bucketName = bucket.getName();
      bucketElement.setText(bucketName);
      bucketsElement.addContent(bucketElement);
    }
    return bucketsElement;
  }
}
