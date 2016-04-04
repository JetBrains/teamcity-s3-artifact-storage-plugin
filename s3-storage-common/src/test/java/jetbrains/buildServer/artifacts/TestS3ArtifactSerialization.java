package jetbrains.buildServer.artifacts;

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

    final S3Artifact a = new S3Artifact("my/relative/path with spaces", "http://some.url", 100L);
    final S3Artifact b = new S3Artifact(a.toSerialized());

    assertThat(b).isEqualToComparingFieldByField(a);
  }
}
