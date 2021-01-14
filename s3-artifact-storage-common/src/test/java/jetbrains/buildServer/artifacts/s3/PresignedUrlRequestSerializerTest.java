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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.artifacts.s3.transport.*;
import jetbrains.buildServer.util.TestFor;
import org.jetbrains.annotations.NotNull;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Created by Evgeniy Koshkin (evgeniy.koshkin@jetbrains.com) on 24.07.17.
 */
@Test
public class PresignedUrlRequestSerializerTest extends BaseTestCase {
  @BeforeClass
  public void beforeClass() {
    setInternalProperty("teamcity.xmlParser.cacheGrammars", "false");
  }

  public void testMultipartMapping() {
    final List<PresignedUrlPartDto> presignedUrlParts = Arrays.asList(new PresignedUrlPartDto("http://someUrl1", 1),
                                                                      new PresignedUrlPartDto("http://someurl2", 2));
    final PresignedUrlDto response1 = PresignedUrlDto.multiPart("some key 1", "upload1", presignedUrlParts);
    final PresignedUrlDto response2 = PresignedUrlDto.singlePart("some key 2", "http://someurl3");
    final PresignedUrlListResponseDto initial = PresignedUrlListResponseDto.createV2(Arrays.asList(response1, response2));
    final String serialized = PresignedUrlRequestSerializer.serializeResponseV2(initial);
    Assert.assertFalse(serialized.isEmpty());
    final PresignedUrlListResponseDto deserialized = PresignedUrlRequestSerializer.deserializeResponseV2(serialized);
    Assert.assertEquals(initial.isVersion2, deserialized.isVersion2);
    Assert.assertEquals(extractObjectKeys(deserialized), extractObjectKeys(initial));
  }

  @Test
  public void testPreSignUrlMapping() {
    final PresignedUrlListResponseDto initial = PresignedUrlListResponseDto.createV1(Arrays.asList(PresignedUrlDto.singlePart("some key 1", "http://some url 1"),
                                                                                                   PresignedUrlDto.singlePart("some key 2", "http://some url 2")));
    final String writtenData = PresignedUrlRequestSerializer.serializeResponseV1(initial);
    Assert.assertFalse(writtenData.isEmpty());
    PresignedUrlListResponseDto deserialized = PresignedUrlRequestSerializer.deserializeResponseV1(writtenData);
    Assert.assertEquals(extractObjectKeys(deserialized), extractObjectKeys(initial));
  }

  @Test
  public void testS3ObjectKeys() {
    Collection<String> initial = new HashSet<>();
    initial.add("one key");
    initial.add("other key");
    String writtenData = PresignedUrlRequestSerializer.serializeRequestV1(initial);
    Assert.assertFalse(writtenData.isEmpty());
    final PresignedUrlListRequestDto presignedUrlListRequestDto = PresignedUrlRequestSerializer.deserializeRequest(writtenData);
    assertSameElements(extractObjectKeys(presignedUrlListRequestDto), initial);
  }

  @TestFor(issues = "TW-67097")
  public void testS3ObjectKeysWithDoubleSpaces() {
    Collection<String> initial = new HashSet<>();
    initial.add("a  b");
    String writtenData = PresignedUrlRequestSerializer.serializeRequestV1(initial);
    Assert.assertFalse(writtenData.isEmpty());
    final PresignedUrlListRequestDto readData = PresignedUrlRequestSerializer.deserializeRequest(writtenData);
    Assert.assertEquals(extractObjectKeys(readData), initial);
  }

  public void testRequestBackwardsCompatibilityWith_2020_2() {
    final List<String> initialKeys = Arrays.asList("other key", "one key");
    final String oldData = OldS3PreSignUrlHelper.writeS3ObjectKeys(initialKeys);

    final PresignedUrlListRequestDto requestList = PresignedUrlRequestSerializer.deserializeRequest(oldData);
    assertSameElements(requestList.presignedUrlRequests.stream().map(request -> request.objectKey).collect(Collectors.toList()), initialKeys);

    final Collection<String> deserializedKeys = OldS3PreSignUrlHelper.readS3ObjectKeys(PresignedUrlRequestSerializer.serializeRequestV1(initialKeys));
    assertSameElements(deserializedKeys, initialKeys);
  }

  public void testResponseBackwardsCompatibilityWith_2020_2() throws MalformedURLException {
    final HashMap<String, URL> initialMap = new HashMap<String, URL>() {{
      put("some key", new URL("http://some url"));
      put("another key", new URL("http://another url"));
    }};
    final Collection<String> initialKeys = initialMap.keySet();
    final Collection<String> initialUrls = initialMap.values().stream().map(Objects::toString).collect(Collectors.toList());


    final PresignedUrlListResponseDto response = PresignedUrlRequestSerializer.deserializeResponseV1(OldS3PreSignUrlHelper.writePreSignUrlMapping(initialMap));
    assertSameElements(extractObjectKeys(response), initialKeys);
    assertSameElements(extractUrls(response), initialUrls);

    final Map<String, URL> deserializedMap = OldS3PreSignUrlHelper.readPreSignUrlMapping(PresignedUrlRequestSerializer.serializeResponseV1(response));
    assertContains(deserializedMap, initialMap);
  }

  @NotNull
  private List<String> extractObjectKeys(@NotNull final PresignedUrlListRequestDto request) {
    return request.presignedUrlRequests.stream().map(url -> url.objectKey).collect(Collectors.toList());
  }

  @NotNull
  private List<String> extractObjectKeys(@NotNull final PresignedUrlListResponseDto response) {
    return response.presignedUrls.stream().map(url -> url.objectKey).collect(Collectors.toList());
  }

  @NotNull
  private List<String> extractUrls(@NotNull final PresignedUrlListResponseDto response) {
    return response.presignedUrls.stream().map(url -> url.presignedUrlParts.stream().findFirst().orElseThrow(IllegalArgumentException::new).url).collect(Collectors.toList());
  }
}
