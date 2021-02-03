/*
 * Copyright 2000-2021 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.artifacts.s3.web;

import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.artifacts.ArtifactData;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.artifacts.s3.preSignedUrl.S3PreSignedManager;
import jetbrains.buildServer.artifacts.s3.preSignedUrl.S3PreSignedManagerImpl;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.artifacts.StoredBuildArtifactInfo;
import jetbrains.buildServer.web.ContentSecurityPolicyConfig;
import jetbrains.buildServer.web.openapi.artifacts.ArtifactDownloadProcessor;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;

import static com.amazonaws.HttpMethod.valueOf;

/**
 * @author vbedrosova
 */
public class S3ArtifactDownloadProcessor implements ArtifactDownloadProcessor {

  private final static Logger LOG = Logger.getInstance(S3ArtifactDownloadProcessor.class.getName());

  private final S3PreSignedManager myPreSignedUrlProvider;
  private final ContentSecurityPolicyConfig myContentSecurityPolicyConfig;

  public S3ArtifactDownloadProcessor(@NotNull S3PreSignedManager preSignedUrlProvider,
                                     @NotNull ContentSecurityPolicyConfig contentSecurityPolicyConfig) {
    myPreSignedUrlProvider = preSignedUrlProvider;
    myContentSecurityPolicyConfig = contentSecurityPolicyConfig;
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
    final S3PreSignedManagerImpl.S3Settings settings = myPreSignedUrlProvider.settings(storedBuildArtifactInfo.getStorageSettings());
    final String pathPrefix = S3Util.getPathPrefix(storedBuildArtifactInfo.getCommonProperties());

    final String preSignedUrl = myPreSignedUrlProvider.generateDownloadUrl(valueOf(httpServletRequest.getMethod()), pathPrefix + artifactData.getPath(), settings);

    fixContentSecurityPolicy(preSignedUrl);

    if (isRedirectCachingDisabled()) {
      httpServletResponse.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
      httpServletResponse.setHeader(HttpHeaders.PRAGMA, "no-cache");
      httpServletResponse.setHeader(HttpHeaders.EXPIRES, "0");
    } else {
      httpServletResponse.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=" + settings.getUrlTtlSeconds());
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
      myContentSecurityPolicyConfig.addDirectiveItems("img-src", url.getProtocol() + "://" + url.getHost());
    } catch (MalformedURLException e) {
      LOG.warn(e);
    }
  }
}
