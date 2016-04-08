package jetbrains.buildServer.artifacts.s3.resolve;

import jetbrains.buildServer.artifacts.ArtifactDependency;
import jetbrains.buildServer.artifacts.ArtifactURLProvider;
import jetbrains.buildServer.artifacts.URLContentRetriever;
import jetbrains.buildServer.artifacts.s3.S3Artifact;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Created by Nikita.Skvortsov
 * date: 31.03.2016.
 */
public class S3ArtifactURLProvider implements ArtifactURLProvider {


  @NotNull
  private final URLContentRetriever myContentRetriever;
  @NotNull
  private final ArtifactURLProvider myURLProvider;

  public S3ArtifactURLProvider(@NotNull URLContentRetriever defaultContentRetriever,
                               @NotNull ArtifactURLProvider defaultURLProvider) {
    myContentRetriever = defaultContentRetriever;
    myURLProvider = defaultURLProvider;
  }

  @Nullable
  @Override
  public String getArtifactUrl(@NotNull String baseURL,
                               @NotNull String module,
                               @NotNull String revision,
                               @NotNull String sourcePath,
                               @Nullable String branch) {

    final List<String> data = getS3Index(baseURL, module, revision, branch);
    for (String record : data) {
      final S3Artifact s3Artifact = new S3Artifact(record);
      if (sourcePath.equals(s3Artifact.getPath())) {
        return s3Artifact.getUrl();
      }
    }
    return null;
  }

  @NotNull
  @Override
  public Collection<String> getArtifactSourcePathList(@NotNull String baseUrl, @NotNull ArtifactDependency dep) {
    final List<String> data = getS3Index(baseUrl,
        dep.getSourceExternalId(),
        dep.getRevisionRule().getRevision(),
        dep.getRevisionRule().getBranch());

    List<String> result = new ArrayList<String>(data.size());
    for (String record : data) {
      result.add(new S3Artifact(record).getPath());
    }
    return result;
  }

  private List<String> getS3Index(@NotNull String baseUrl,
                                  @NotNull String sourceExternalId,
                                  @NotNull String revision,
                                  @Nullable String branch) {
    final String s3indexURL = myURLProvider.getArtifactUrl(baseUrl, sourceExternalId, revision,
        S3Constants.S3_ARTIFACTS_LIST_PATH + "/" + S3Constants.S3_ARTIFACTS_LIST, branch);

    if (s3indexURL != null) {
      File tempFile = null;
      try {
        tempFile = FileUtil.createTempFile("s3", "index");
        myContentRetriever.downloadUrlTo(s3indexURL, tempFile);
        return FileUtil.readFile(tempFile);
      } catch (IOException e) {
        Loggers.AGENT.warnAndDebugDetails("Error retrieving s3 index: " + e.getMessage(), e);
      } finally {
        if (tempFile != null) {
          FileUtil.delete(tempFile);
        }
      }
    }
    return Collections.emptyList();
  }
}
