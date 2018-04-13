package jetbrains.buildServer.artifacts.s3.web;

import com.amazonaws.HttpMethod;
import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.artifacts.ArtifactData;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.artifacts.s3.preSignedUrl.S3PreSignedUrlProvider;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.artifacts.StoredBuildArtifactInfo;
import jetbrains.buildServer.web.openapi.artifacts.ArtifactDownloadProcessor;
import org.apache.http.HttpHeaders;
import org.jetbrains.annotations.NotNull;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * @author vbedrosova
 */
public class S3ArtifactDownloadProcessor implements ArtifactDownloadProcessor {

  private final static Logger LOG = Logger.getInstance(S3ArtifactDownloadProcessor.class.getName());

  private S3PreSignedUrlProvider myPreSignedUrlProvider;

  public S3ArtifactDownloadProcessor(@NotNull S3PreSignedUrlProvider preSignedUrlProvider) {
    myPreSignedUrlProvider = preSignedUrlProvider;
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

    httpServletResponse.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=" + myPreSignedUrlProvider.getUrlLifetimeSec());
    httpServletResponse.sendRedirect(myPreSignedUrlProvider.getPreSignedUrl(HttpMethod.valueOf(httpServletRequest.getMethod()), bucketName, pathPrefix + artifactData.getPath(),params));
    return true;
  }
}
