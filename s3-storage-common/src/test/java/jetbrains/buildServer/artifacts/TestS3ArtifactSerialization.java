package jetbrains.buildServer.artifacts;

import com.google.gson.Gson;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Created by Nikita.Skvortsov
 * date: 24.03.2016.
 */
@Test
public class TestS3ArtifactSerialization {
  public void testSerializeDeserialize() {

    Gson gson = new Gson();
    final ArtifactData a =
      ArtifactData.create("my/relative/path with spaces", 100L)
        .withProperty(ArtifactData.URL_KEY, "http://some.url")
        .withProperty(S3Constants.S3_PATH_PREFIX_ATTR, "fakeKey")
        .withProperty(S3Constants.S3_BUCKET_NAME, "bucket.name");
    final ArtifactData b = gson.fromJson(gson.toJson(a), ArtifactData.class);

    assertThat(b).isEqualToComparingFieldByField(a);
  }
}
