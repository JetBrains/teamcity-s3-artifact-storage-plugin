package jetbrains.buildServer.artifacts;

import com.google.gson.Gson;
import jetbrains.buildServer.artifacts.s3.S3Artifact;
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
    final S3Artifact a = new S3Artifact("my/relative/path with spaces", "http://some.url", 100L, "fakeKey");
    final S3Artifact b = gson.fromJson(gson.toJson(a), S3Artifact.class);

    assertThat(b).isEqualToComparingFieldByField(a);
  }
}
