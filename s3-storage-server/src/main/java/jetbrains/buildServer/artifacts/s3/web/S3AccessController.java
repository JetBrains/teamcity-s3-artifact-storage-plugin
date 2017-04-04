package jetbrains.buildServer.artifacts.s3.web;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.intellij.openapi.util.text.StringUtil;
import jetbrains.buildServer.artifacts.ArtifactListData;
import jetbrains.buildServer.artifacts.ArtifactStorageSettingsProvider;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.BuildsManager;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SecurityContextEx;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.artifacts.ServerArtifactHelper;
import jetbrains.buildServer.serverSide.auth.AuthorityHolder;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import jetbrains.buildServer.util.amazon.AWSException;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URLDecoder;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Created by Nikita.Skvortsov
 * date: 01.02.2017.
 */
public class S3AccessController extends BaseController {

  public static final int URL_LIFETIME_SEC = TeamCityProperties.getInteger(S3Constants.S3_URL_LIFETIME_SEC, S3Constants.DEFAULT_S3_URL_LIFETIME_SEC);

  @NotNull private final ServerArtifactHelper myHelper;
  @NotNull private final ArtifactStorageSettingsProvider mySettingsProvider;
  @NotNull private final BuildsManager myBuildsManager;
  @NotNull private final SecurityContext mySecurityContext;

  private final Cache<String, String> myLinksCache = CacheBuilder.newBuilder()
                                                                 .expireAfterWrite(URL_LIFETIME_SEC, TimeUnit.SECONDS)
                                                                 .maximumSize(100)
                                                                 .build();

  public S3AccessController(@NotNull final WebControllerManager controllerManager,
                            @NotNull final ServerArtifactHelper helper,
                            @NotNull final ArtifactStorageSettingsProvider settingsProvider,
                            @NotNull final BuildsManager buildsManager,
                            @NotNull final SecurityContext securityContext) {
    myHelper = helper;
    mySettingsProvider = settingsProvider;
    myBuildsManager = buildsManager;
    mySecurityContext = securityContext;
    controllerManager.registerController(S3Constants.S3_ACCESS_CONTROLLER_PATH, this);
  }

  @Nullable
  @Override
  protected ModelAndView doHandle(@NotNull HttpServletRequest httpServletRequest, @NotNull HttpServletResponse httpServletResponse) throws Exception {
    final AuthorityHolder authorityHolder = mySecurityContext.getAuthorityHolder();

    try {
      return ((SecurityContextEx) mySecurityContext).runAs(authorityHolder, () -> {
        final Long buildId = Long.valueOf(httpServletRequest.getParameter("buildId"));
        final SBuild build = myBuildsManager.findBuildInstanceById(buildId);
        if (build == null) {
          httpServletResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
          httpServletResponse.getWriter().write("Build " + buildId + " not found");
          return null;
        }

        final String path = URLDecoder.decode(httpServletRequest.getParameter("path"), "UTF-8");
        if (StringUtil.isEmpty(path)) {
          httpServletResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          httpServletResponse.getWriter().write("Path should not be empty");
          return null;
        }

        final ArtifactListData artifactsInfo = myHelper.getArtifactList(build);
        if (artifactsInfo == null || S3Util.getPathPrefix(artifactsInfo) == null) {
          httpServletResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
          httpServletResponse.getWriter().write("No S3 artifacts found for build " + buildId);
          return null;
        }

        final Map<String, String> params = S3Util.validateParameters(mySettingsProvider.getStorageSettings(String.valueOf(build.getBuildId())));
        final String pathPrefix = S3Util.getPathPrefix(artifactsInfo);

        return artifactsInfo.getArtifactList()
          .stream()
          .filter(artifact -> path.equals(artifact.getPath()))
          .findFirst()
          .map(artifact -> getTemporaryUrl(pathPrefix + artifact.getPath(), params))
          .map(url -> new ModelAndView(new RedirectView(url)))
          .orElse(null);
      });
    } catch (Throwable t) {
      throw new Exception("Failed to process request", t);
    }
  }

  private String getTemporaryUrl(@NotNull String key, @NotNull Map<String, String> params) {
    final String bucketName = S3Util.getBucketName(params);
    try {
      return myLinksCache.get(getIdentity(params, key, bucketName), () -> AWSCommonParams.withAWSClients(params, awsClients -> {
        final GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketName, key, HttpMethod.GET).withExpiration(new Date(System.currentTimeMillis() + URL_LIFETIME_SEC * 1000));
        return awsClients.createS3Client().generatePresignedUrl(request).toString();
      }));
    } catch (ExecutionException e) {
      logDetails(e.getCause());
      Loggers.AGENT.infoAndDebugDetails("Failed to create pre-signed URL for [" + key + "] in bucket [" + bucketName + "]", e);
    }
    return null;
  }

  @NotNull
  private String getIdentity(@NotNull Map<String, String> params, @NotNull String key, @NotNull String bucket) {
    return String.valueOf(AWSCommonParams.calculateIdentity("", params, bucket, key));
  }

  private void logDetails(@NotNull Throwable t) {
    final String details = new AWSException(t.getCause()).getDetails();
    if (StringUtil.isNotEmpty(details)) {
      Loggers.AGENT.info(details);
    }
  }
}
