

package jetbrains.buildServer.artifacts.s3.web;

import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.ExtensionsProvider;
import jetbrains.buildServer.artifacts.ArtifactData;
import jetbrains.buildServer.artifacts.s3.PresignedUrlWithTtl;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.artifacts.s3.cloudfront.CloudFrontEnabledPresignedUrlProvider;
import jetbrains.buildServer.artifacts.s3.cloudfront.CloudFrontSettings;
import jetbrains.buildServer.artifacts.s3.cloudfront.RequestMetadata;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.ProjectManagerEx;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.artifacts.StoredBuildArtifactInfo;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.ssh.ServerSshKeyManager;
import jetbrains.buildServer.web.ContentSecurityPolicyConfig;
import jetbrains.buildServer.web.openapi.artifacts.ArtifactDownloadProcessor;
import jetbrains.buildServer.web.util.WebUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpHeaders;

import static com.amazonaws.HttpMethod.valueOf;
import static jetbrains.buildServer.artifacts.s3.S3Constants.PROJECT_ID_PARAM;

/**
 * @author vbedrosova
 */
public class S3ArtifactDownloadProcessor implements ArtifactDownloadProcessor {

  private final static Logger LOG = Logger.getInstance(S3ArtifactDownloadProcessor.class.getName());

  private final CloudFrontEnabledPresignedUrlProvider myPreSignedUrlProvider;
  private final ExtensionsProvider myExtensionsProvider;
  private final ContentSecurityPolicyConfig myContentSecurityPolicyConfig;
  private final ProjectManagerEx myProjectManager;

  public S3ArtifactDownloadProcessor(@NotNull CloudFrontEnabledPresignedUrlProvider preSignedUrlProvider,
                                     @NotNull ExtensionsProvider extensionsProvider,
                                     @NotNull ContentSecurityPolicyConfig contentSecurityPolicyConfig,
                                     @NotNull ProjectManagerEx projectManager) {
    myPreSignedUrlProvider = preSignedUrlProvider;
    myExtensionsProvider = extensionsProvider;
    myContentSecurityPolicyConfig = contentSecurityPolicyConfig;
    myProjectManager = projectManager;
  }

  @NotNull
  @Override
  public String getType() {
    return S3Constants.S3_STORAGE_TYPE;
  }

  @Override
  public boolean processDownload(@NotNull StoredBuildArtifactInfo storedBuildArtifactInfo,
                                 @NotNull BuildPromotion buildPromotion,
                                 @NotNull HttpServletRequest httpServletRequest,
                                 @NotNull HttpServletResponse httpServletResponse) throws IOException {
    final ArtifactData artifactData = storedBuildArtifactInfo.getArtifactData();
    if (artifactData == null) throw new IOException("Can not process artifact download request for a folder");

    final String pathPrefix = S3Util.getPathPrefix(storedBuildArtifactInfo.getCommonProperties());

    Map<String, String> projectParameters = new HashMap<>();
    projectParameters.put(PROJECT_ID_PARAM, buildPromotion.getProjectId());

    final ProjectEx project = myProjectManager.findProjectById(buildPromotion.getProjectId());
    if (project != null) {
      projectParameters.putAll(project.getParameters());
    }
    final Map<String, String> storageSettings = storedBuildArtifactInfo.getStorageSettings();

    final String objectKey = pathPrefix + artifactData.getPath();
    String requestRegion = httpServletRequest.getHeader(S3Constants.S3_REGION_HEADER_NAME);
    String userAgent = WebUtil.getUserAgent(httpServletRequest);
    CloudFrontSettings settings = myPreSignedUrlProvider.settings(storageSettings, projectParameters, RequestMetadata.from(requestRegion, userAgent));

    PresignedUrlWithTtl presignedUrlWithTtl = myPreSignedUrlProvider.generateDownloadUrl(valueOf(httpServletRequest.getMethod()), objectKey, settings);
    String preSignedUrl = presignedUrlWithTtl.getUrl();
    fixContentSecurityPolicy(preSignedUrl);

    if (isRedirectCachingDisabled()) {
      httpServletResponse.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
      httpServletResponse.setHeader(HttpHeaders.PRAGMA, "no-cache");
      httpServletResponse.setHeader(HttpHeaders.EXPIRES, "0");
    } else {
      httpServletResponse.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=" + presignedUrlWithTtl.getUrlTtlSeconds());
    }
    httpServletResponse.sendRedirect(preSignedUrl);
    return true;
  }

  /**
   * The redirect logic is messing up with user's network settings when user turns on/off VPN for example.
   * This toggle makes it possible to turn the redirect caching off. See ADM-49889
   */
  private boolean isRedirectCachingDisabled() {
    return !TeamCityProperties.getBooleanOrTrue("teamcity.internal.storage.s3.download.cacheRedirect.enabled");
  }

  private void fixContentSecurityPolicy(final String preSignedUrl) {
    try {
      final URL url = new URL(preSignedUrl);
      myContentSecurityPolicyConfig.addDirectiveItems("img-src", url.getProtocol() + "://" + url.getHost() + (url.getPort() > 0 ? ":" + url.getPort() : ""));
    } catch (MalformedURLException e) {
      LOG.warn(e);
    }
  }

  @Nullable
  private ServerSshKeyManager getSshKeyManager() {
    Collection<ServerSshKeyManager> managers = myExtensionsProvider.getExtensions(ServerSshKeyManager.class);
    if (managers.isEmpty())
      return null;
    return managers.iterator().next();
  }
}
