package jetbrains.buildServer.artifacts.s3;

import com.intellij.openapi.diagnostic.Logger;
import eu.bitwalker.useragentutils.BrowserType;
import eu.bitwalker.useragentutils.UserAgent;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;
import jetbrains.buildServer.artifacts.s3.amazonClient.AmazonS3Provider;
import jetbrains.buildServer.artifacts.s3.cloudfront.CloudFrontSettings;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.connections.credentials.ConnectionCredentialsException;
import org.jetbrains.annotations.NotNull;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_DOWNLOAD_THRESHOLD_FOR_PRESIGN_URL_EXTENSION_IN_GB;

public abstract class PresignedUrlProvider {

  private static final Logger LOG = Logger.getInstance(PresignedUrlProvider.class.getName());

  protected final AmazonS3Provider myAmazonS3Provider;

  public PresignedUrlProvider(AmazonS3Provider amazonS3Provider) {
    myAmazonS3Provider = amazonS3Provider;
  }

  protected  <T> T callS3Presign(@NotNull final Function<S3Presigner, T> callable, @NotNull final S3Settings settings) throws IOException {
    try {
      String projectId = settings.getProjectId();
      if (projectId == null) {
        throw new ConnectionCredentialsException("Cannot generate presigned url, project ID is not provided");
      }
      return myAmazonS3Provider.withS3PresignerShuttingDownImmediately(
        settings.getBucketName(),
        settings.toRawSettings(),
        projectId,
        presigner -> {
          try {
            return callable.apply(presigner);
          } catch (final Throwable t) {
            throw new IOException(t);
          }
        });
    } catch (ConnectionCredentialsException e) {
      throw new IOException(e);
    }
  }

  protected  <T> T callS3(@NotNull final Function<S3Client, T> callable, @NotNull final S3Settings settings) throws IOException {
    try {
      String projectId = settings.getProjectId();
      if (projectId == null) {
        throw new ConnectionCredentialsException("Cannot generate PresignedUrl, project ID is not provided");
      }
      return myAmazonS3Provider.withS3ClientShuttingDownImmediately(
        settings.toRawSettings(),
        projectId,
        client -> {
          try {
            return callable.apply(client);
          } catch (final Throwable t) {
            throw new IOException(t);
          }
        });
    } catch (ConnectionCredentialsException e) {
      throw new IOException(e);
    }
  }

  protected int getUrlTtlSeconds(@NotNull String objectKey, @NotNull S3Settings settings, boolean isDownload) {
    return isDownload && isUrlTtlExtended(settings, objectKey) ? settings.getUrlExtendedTtlSeconds() : settings.getUrlTtlSeconds();
  }

  @NotNull
  protected Optional<HeadObjectResponse> getObjectMetadata(@NotNull String objectKey, @NotNull S3Settings settings) {
    HeadObjectResponse metadata = null;

    try {
      metadata = callS3(client -> client.headObject(b -> b.bucket(settings.getBucketName()).key(objectKey)), settings);
    } catch (Exception e) {
      LOG.debug("Metadata not found for object " + objectKey + " in a bucket " + settings.getBucketName(), e);
    }

    return Optional.ofNullable(metadata);
  }

  protected boolean isUrlTtlExtended(@NotNull S3Settings settings, @NotNull String objectKey) {
    if (!(settings instanceof CloudFrontSettings)) {
      return false;
    }
    CloudFrontSettings cloudFrontSettings = (CloudFrontSettings) settings;
    if (!isBrowser(cloudFrontSettings.getRequestUserAgent())) {
      return false;
    }
    Optional<HeadObjectResponse> objectMetadata = getObjectMetadata(objectKey, settings);
    long contentLength = objectMetadata.map(HeadObjectResponse::contentLength).orElse(0L);
    return contentLength > TeamCityProperties.getInteger(S3_DOWNLOAD_THRESHOLD_FOR_PRESIGN_URL_EXTENSION_IN_GB, 1) * Math.pow(2, 30);
  }

  private static boolean isBrowser(final String userAgentString) {
    if (TeamCityProperties.getBoolean("teamcity.http.auth.treat.all.clients.as.browsers")) return true;
    final UserAgent userAgent = UserAgent.parseUserAgentString(userAgentString);
    BrowserType browserType = userAgent.getBrowser().getBrowserType();
    return browserType == BrowserType.WEB_BROWSER || browserType == BrowserType.MOBILE_BROWSER;
  }
}
