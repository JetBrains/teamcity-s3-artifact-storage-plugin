

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
 * @author Dmitrii Bogdanov
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
    Assert.assertEquals(initial.isVersion2(), deserialized.isVersion2());
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
    assertSameElements(requestList.getPresignedUrlRequests().stream().map(request -> request.getObjectKey()).collect(Collectors.toList()), initialKeys);

    final Collection<String> deserializedKeys = OldS3PreSignUrlHelper.readS3ObjectKeys(PresignedUrlRequestSerializer.serializeRequestV1(initialKeys));
    assertSameElements(deserializedKeys, initialKeys);
  }

  public void testResponseBackwardsCompatibilityWith_2020_2() throws MalformedURLException {
    final HashMap<String, URL> initialMap = new HashMap<String, URL>() {{
      put("some key", new URL("http://some%20url"));
      put("another key", new URL("http://another%20url"));
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
    return request.getPresignedUrlRequests().stream().map(url -> url.getObjectKey()).collect(Collectors.toList());
  }

  @NotNull
  private List<String> extractObjectKeys(@NotNull final PresignedUrlListResponseDto response) {
    return response.getPresignedUrls().stream().map(url -> url.getObjectKey()).collect(Collectors.toList());
  }

  @NotNull
  private List<String> extractUrls(@NotNull final PresignedUrlListResponseDto response) {
    return response.getPresignedUrls().stream()
                   .map(url -> url.getPresignedUrlParts().stream().findFirst().orElseThrow(IllegalArgumentException::new).getUrl())
                   .collect(Collectors.toList());
  }
}
