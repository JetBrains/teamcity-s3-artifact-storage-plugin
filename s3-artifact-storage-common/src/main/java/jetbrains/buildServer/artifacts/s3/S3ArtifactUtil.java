package jetbrains.buildServer.artifacts.s3;

import java.util.ArrayList;
import java.util.List;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class S3ArtifactUtil {
  public static String getPathPrefix(@Nullable final String customPrefix, @NotNull final String projectId, @NotNull final String buildTypeId, final long buildId) {
    final List<String> pathSegments = new ArrayList<>();
    if (!StringUtil.isEmptyOrSpaces(customPrefix)) {
      pathSegments.add(customPrefix);
    }
    pathSegments.add(projectId);
    pathSegments.add(buildTypeId);
    pathSegments.add(Long.toString(buildId));
    return StringUtil.join("/", pathSegments) + "/";
  }
}
