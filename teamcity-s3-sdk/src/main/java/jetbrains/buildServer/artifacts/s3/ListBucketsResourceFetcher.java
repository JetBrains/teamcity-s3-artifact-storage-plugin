/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

import com.amazonaws.services.s3.AmazonS3;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.*;

import com.amazonaws.services.s3.model.Bucket;
import jetbrains.buildServer.Used;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.artifacts.s3.S3Util.withClientCorrectingRegion;

/**
 * Gets a list of buckets in S3 storage.
 */
public class ListBucketsResourceFetcher extends S3ClientResourceFetcher<ListBucketsResourceFetcher.ListBucketsDto> {
  @Override
  public ListBucketsDto fetchDto(final Map<String, String> parameters) {
    return S3Util.withS3Client(parameters, s3Client -> {
      AWSCommonParams.validate(parameters, true);
      List<BucketDto> bucketList = withClientCorrectingRegion(s3Client, copyMap(parameters), AmazonS3::listBuckets)
        .stream()
        .map(Bucket::getName)
        .map(BucketDto::new)
        .collect(Collectors.toList());
      return new ListBucketsDto(bucketList);
    });
  }

  @NotNull
  private Map<String, String> copyMap(final Map<String, String> parameters) {
    return new HashMap<>(parameters);
  }

  @XmlRootElement(name = "buckets")
  @XmlAccessorType(XmlAccessType.PROPERTY)
  public static class ListBucketsDto implements S3Dto {
    @NotNull
    private final List<BucketDto> buckets;

    @Used("xml-serialization")
    public ListBucketsDto() {
      this.buckets = null;
    }

    public ListBucketsDto(@NotNull final List<BucketDto> buckets) {
      this.buckets = buckets;
    }

    @XmlElement(name = "bucket")
    @XmlList
    @NotNull
    public List<BucketDto> getBuckets() {
      return buckets;
    }
  }

  @XmlRootElement(name = "bucket")
  public static class BucketDto {
    @NotNull
    private final String name;

    @Used("xml-serialization")
    public BucketDto() {
      this.name = null;
    }

    public BucketDto(@NotNull String name) {
      this.name = name;
    }

    @NotNull
    @XmlValue
    public String getName() {
      return name;
    }
  }
}
