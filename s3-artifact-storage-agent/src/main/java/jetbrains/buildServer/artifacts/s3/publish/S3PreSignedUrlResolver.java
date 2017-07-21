package jetbrains.buildServer.artifacts.s3.publish;

import org.jetbrains.annotations.NotNull;

import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Created by Evgeniy Koshkin (evgeniy.koshkin@jetbrains.com) on 20.07.17.
 */
class S3PreSignedUrlResolver {
  @NotNull
  Map<String, URL> resolveUploadUrls(@NotNull Collection<String> s3ObjectKeys) {
    //TODO: implement
    return Collections.emptyMap();
  }
}
