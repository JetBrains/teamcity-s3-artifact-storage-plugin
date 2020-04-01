/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import java.util.Map;
import jetbrains.buildServer.artifacts.s3.S3Util;
import org.jdom.Content;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

public class BucketLocationHandler extends S3ClientResourceHandler {

  private static String getRegionName(@Nullable String location) {
    if (location == null) {
      return Regions.US_EAST_1.getName();
    }

    final Region region = RegionUtils.getRegion(location);
    if (region == null && location.equals("US")) {
      return Regions.US_EAST_1.getName();
    }

    return region != null ? region.getName() : location;
  }

  @Override
  protected Content getContent(final AmazonS3 s3Client, final Map<String, String> parameters) {
    final String bucketName = S3Util.getBucketName(parameters);
    if (bucketName == null) {
      final String message = String.format("Invalid request: %s parameter was not set", S3Util.beanPropertyNameForBucketName());
      throw new IllegalArgumentException(message);
    }
    final Element bucketElement = new Element("bucket");
    bucketElement.setAttribute("name", bucketName);
    bucketElement.setAttribute("location", S3Util.withClientCorrectingRegion(s3Client, parameters, correctedClient -> getRegionName(correctedClient.getBucketLocation(bucketName))));
    return bucketElement;
  }
}
