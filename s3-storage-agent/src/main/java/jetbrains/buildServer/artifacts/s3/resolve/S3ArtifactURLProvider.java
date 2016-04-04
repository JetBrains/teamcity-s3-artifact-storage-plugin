package jetbrains.buildServer.artifacts.s3.resolve;

import jetbrains.buildServer.artifacts.ArtifactDependency;
import jetbrains.buildServer.artifacts.ArtifactURLProvider;
import jetbrains.buildServer.artifacts.s3.Constants;
import jetbrains.buildServer.artifacts.s3.S3Artifact;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.util.StringUtil;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Created by Nikita.Skvortsov
 * date: 31.03.2016.
 */
public class S3ArtifactURLProvider implements ArtifactURLProvider {

  private static final String S3_ARTIFACTS_LIST = "[URL]/httpAuth/repository/download/[module]/[revision]/"
      + Constants.S3_ARTIFACTS_LIST_PATH + "/" + Constants.S3_ARTIFACTS_LIST;
  @NotNull
  private final HttpClient client;

  public S3ArtifactURLProvider(@NotNull  HttpClient client) {
    this.client = client;
  }

  @Nullable
  @Override
  public String getArtifactUrl(@NotNull String baseURL,
                               @NotNull String module,
                               @NotNull String revision,
                               @NotNull String sourcePath,
                               @Nullable String branch) {
    String filledUrl = StringUtil.replace(S3_ARTIFACTS_LIST, "[URL]", baseURL);
    String filledModule = StringUtil.replace(filledUrl, "[module]", urlEncode(module));
    String filledRevision = StringUtil.replace(filledModule, "[revision]", urlEncode(revision));
    if (branch != null) {
      filledRevision = filledRevision + "?branch=" + urlEncode(branch);
    }
    final GetMethod getDescriptor = new GetMethod(filledRevision);
    try {
      final int responseCode = client.executeMethod(getDescriptor);
      if (responseCode == 200) {
        String[] data = getDescriptor.getResponseBodyAsString().split("\n");
        for (String record : data) {
          final S3Artifact s3Artifact = new S3Artifact(record);
          if (sourcePath.equals(s3Artifact.getPath())) {
            return s3Artifact.getUrl();
          }
        }
      }
    } catch (IOException e) {
      Loggers.AGENT.warnAndDebugDetails("Error retrieving s3 index: " + e.getMessage(), e);
    }
    return null;
  }

  @NotNull
  @Override
  public Collection<String> getArtifactSourcePathList(@NotNull String baseUrl, @NotNull ArtifactDependency dep) {
    String filledUrl = StringUtil.replace(S3_ARTIFACTS_LIST, "[URL]", baseUrl);
    String filledModule = StringUtil.replace(filledUrl, "[module]", urlEncode(dep.getSourceExternalId()));
    String filledRevision = StringUtil.replace(filledModule, "[revision]", urlEncode(dep.getRevisionRule().getRevision()));
    if (dep.getRevisionRule().getBranch() != null) {
      filledRevision = filledRevision + "?branch=" + urlEncode(dep.getRevisionRule().getBranch());
    }
    final GetMethod getDescriptor = new GetMethod(filledRevision);
    try {
      final int responseCode = client.executeMethod(getDescriptor);
      if (responseCode == 200) {
        String[] data = getDescriptor.getResponseBodyAsString().split("\n");
        List<String> result = new ArrayList<String>(data.length);
        for (String record : data) {
          result.add(new S3Artifact(record).getPath());
        }
        return result;
      }
    } catch (IOException e) {
      Loggers.AGENT.warnAndDebugDetails("Error retrieving s3 index: " + e.getMessage(), e);
    }
    return Collections.emptySet();
  }


  @NotNull
  private static String urlEncode(@NotNull final String text) {
    try {
      return URLEncoder.encode(text, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      Loggers.AGENT.warn(e.toString());
      Loggers.AGENT.debug(e);
    }
    return text;
  }
}
