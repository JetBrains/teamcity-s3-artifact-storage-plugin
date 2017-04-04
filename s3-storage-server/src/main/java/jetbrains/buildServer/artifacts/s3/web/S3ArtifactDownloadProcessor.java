package jetbrains.buildServer.artifacts.s3.web;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import jetbrains.buildServer.artifacts.ArtifactData;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.artifacts.StoredBuildArtifactInfo;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import jetbrains.buildServer.util.amazon.AWSException;
import jetbrains.buildServer.web.openapi.artifacts.ArtifactDownloadProcessor;
import org.jetbrains.annotations.NotNull;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * @author vbedrosova
 */
public class S3ArtifactDownloadProcessor implements ArtifactDownloadProcessor {

  private final static Logger LOG = Logger.getInstance(S3ArtifactDownloadProcessor.class.getName());

  public static final int URL_LIFETIME_SEC = TeamCityProperties.getInteger(S3Constants.S3_URL_LIFETIME_SEC, S3Constants.DEFAULT_S3_URL_LIFETIME_SEC);

  private final Cache<String, String> myLinksCache = CacheBuilder.newBuilder()
    .expireAfterWrite(URL_LIFETIME_SEC, TimeUnit.SECONDS)
    .maximumSize(100)
    .build();

  @NotNull
  @Override
  public String getType() {
    return S3Constants.S3_STORAGE_TYPE;
  }

  @Override
  public void processDownload(@NotNull StoredBuildArtifactInfo storedBuildArtifactInfo,
                              @NotNull SBuild sBuild,
                              @NotNull HttpServletRequest httpServletRequest,
                              @NotNull HttpServletResponse httpServletResponse) throws IOException {
    final ArtifactData artifactData = storedBuildArtifactInfo.getArtifactData();
    if (artifactData == null) throw new IOException("Can not process artifact download request for a folder");

    final Map<String, String> params = S3Util.validateParameters(storedBuildArtifactInfo.getStorageSettings());
    final String pathPrefix = S3Util.getPathPrefix(storedBuildArtifactInfo.getCommonProperties());

    httpServletResponse.sendRedirect(getTemporaryUrl(pathPrefix + artifactData.getPath(),params));
  }

  @NotNull
  private String getTemporaryUrl(@NotNull String key, @NotNull Map<String, String> params) throws IOException {
    final String bucketName = S3Util.getBucketName(params);
    try {
      return myLinksCache.get(getIdentity(params, key, bucketName), () -> AWSCommonParams.withAWSClients(params, awsClients -> {
        final GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketName, key, HttpMethod.GET).withExpiration(new Date(System.currentTimeMillis() + URL_LIFETIME_SEC * 1000));
        return awsClients.createS3Client().generatePresignedUrl(request).toString();
      }));
    } catch (ExecutionException e) {
      final AWSException awsException = new AWSException(e.getCause());
      if (StringUtil.isNotEmpty(awsException.getDetails())) {
        LOG.warn(awsException.getDetails());
      }
      final String err = "Failed to create pre-signed URL for [" + key + "] in bucket [" + bucketName + "]";
      LOG.infoAndDebugDetails(err, awsException);
      throw new IOException(err + ": " + awsException.getMessage(), awsException);
    }
  }

  @NotNull
  private String getIdentity(@NotNull Map<String, String> params, @NotNull String key, @NotNull String bucket) {
    return String.valueOf(AWSCommonParams.calculateIdentity("", params, bucket, key));
  }
}
