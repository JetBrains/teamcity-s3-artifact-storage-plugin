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

package jetbrains.buildServer.artifacts.s3;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import java.util.Map;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.Used;
import org.jetbrains.annotations.Nullable;

public class BucketLocationFetcher extends S3ClientResourceFetcher<BucketLocationFetcher.BucketLocationDto> {

  public static String getRegionName(@Nullable String location) {
    if (location == null) {
      return Regions.US_EAST_1.getName();
    }

    final Region region = RegionUtils.getRegion(location);
    if (region == null && location.equals("US")) {
      return Regions.US_EAST_1.getName();
    }
    if (region != null) {
      return !"US".equals(region.getName()) ? region.getName() : Regions.US_EAST_1.getName();
    } else {
      return location;
    }
  }

  @Override
  protected BucketLocationDto fetchDto(final AmazonS3 s3Client, final Map<String, String> parameters) {
    final String bucketName = S3Util.getBucketName(parameters);
    if (bucketName == null) {
      final String message = String.format("Invalid request: %s parameter was not set", S3Util.beanPropertyNameForBucketName());
      throw new IllegalArgumentException(message);
    }
    return new BucketLocationDto(bucketName, S3Util.withClientCorrectingRegion(s3Client, parameters, correctedClient -> getRegionName(correctedClient.getBucketLocation(bucketName))));
  }

  @XmlRootElement(name = "bucket")
  public static class BucketLocationDto implements S3Dto {
    private final String name;
    private final String location;

    @Used("xml-serialization")
    public BucketLocationDto() {
      this.name = null;
      this.location = null;
    }

    public BucketLocationDto(String name, String location) {
      this.name = name;
      this.location = location;
    }

    @XmlAttribute(name = "name")
    public String getName() {
      return name;
    }

    @XmlAttribute(name = "location")
    public String getLocation() {
      return location;
    }
  }
}
