/*
 * Copyright 2000-2020 JetBrains s.r.o.
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
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.artifacts.ArtifactData;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.artifacts.s3.preSignedUrl.S3PreSignedUrlProvider;
import jetbrains.buildServer.serverSide.BuildPromotion;
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

  private final S3PreSignedUrlProvider myPreSignedUrlProvider;
  private final ContentSecurityPolicyConfig myContentSecurityPolicyConfig;

  public S3ArtifactDownloadProcessor(@NotNull S3PreSignedUrlProvider preSignedUrlProvider,
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

    final Map<String, String> params = S3Util.validateParameters(storedBuildArtifactInfo.getStorageSettings());
    final String pathPrefix = S3Util.getPathPrefix(storedBuildArtifactInfo.getCommonProperties());

    final String bucketName = S3Util.getBucketName(params);
    if (bucketName == null) {
      final String message = "Failed to create pre-signed URL: bucket name is not specified, check S3 storage profile settings";
      LOG.warn(message);
      throw new IOException(message);
    }

    final String preSignedUrl = myPreSignedUrlProvider.getPreSignedUrl(valueOf(httpServletRequest.getMethod()), bucketName, pathPrefix + artifactData.getPath(), params);

    fixContentSecurityPolicy(preSignedUrl);

    httpServletResponse.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=" + myPreSignedUrlProvider.getUrlLifetimeSec());
    httpServletResponse.sendRedirect(preSignedUrl);
    return true;
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
