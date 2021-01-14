/*
 * Copyright 2000-2021 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.artifacts.s3.web;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.Bucket;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import org.jdom.Content;
import org.jdom.Element;

import static jetbrains.buildServer.artifacts.s3.S3Util.withClientCorrectingRegion;

/**
 * Gets a list of buckets in S3 storage.
 */
public class BucketsResourceHandler extends S3ClientResourceHandler {
  @Override
  public Content getContent(final AmazonS3 s3Client, final Map<String, String> parameters) {
    AWSCommonParams.validate(parameters, true);
    final Element bucketsElement = new Element("buckets");
    for (Bucket bucket : withClientCorrectingRegion(s3Client, new HashMap<>(parameters), AmazonS3::listBuckets)) {
      final Element bucketElement = new Element("bucket");
      final String bucketName = bucket.getName();
      bucketElement.setText(bucketName);
      bucketsElement.addContent(bucketElement);
    }
    return bucketsElement;
  }
}
