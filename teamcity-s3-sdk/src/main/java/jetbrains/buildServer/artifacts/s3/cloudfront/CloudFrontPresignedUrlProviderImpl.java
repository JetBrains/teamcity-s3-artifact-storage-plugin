package jetbrains.buildServer.artifacts.s3.cloudfront;

import software.amazon.awssdk.services.cloudfront.internal.auth.Pem;
import software.amazon.awssdk.services.cloudfront.model.CannedSignerRequest;
import software.amazon.awssdk.services.cloudfront.model.CloudFrontException;
import software.amazon.awssdk.services.cloudfront.model.GetDistributionRequest;
import software.amazon.awssdk.services.cloudfront.CloudFrontUtilities;
import software.amazon.awssdk.utils.http.SdkHttpUtils;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import jetbrains.buildServer.artifacts.s3.PresignedUrlWithTtl;
import jetbrains.buildServer.artifacts.s3.PresignedUrlProvider;
import jetbrains.buildServer.artifacts.s3.amazonClient.AmazonS3Provider;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.connections.credentials.ConnectionCredentialsException;
import jetbrains.buildServer.util.TimeService;
import jetbrains.buildServer.util.amazon.AWSException;
import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.artifacts.s3.cloudfront.CloudFrontConstants.S3_CLOUDFRONT_DOMAIN_NAME_CACHE_EXPIRATION_HOURS;

public class CloudFrontPresignedUrlProviderImpl extends PresignedUrlProvider implements CloudFrontPresignedUrlProvider {
  @NotNull
  private static final Logger LOG = Logger.getInstance(CloudFrontPresignedUrlProviderImpl.class.getName());

  @NotNull
  private static final CloudFrontUtilities CLOUD_FRONT_UTILITIES = CloudFrontUtilities.create();

  @NotNull
  private final TimeService myTimeService;

  @NotNull
  private final Cache<String, String> domainNameCache = CacheBuilder.newBuilder()
                                                                    .expireAfterWrite(TeamCityProperties.getInteger(S3_CLOUDFRONT_DOMAIN_NAME_CACHE_EXPIRATION_HOURS, 1),
                                                                                      TimeUnit.HOURS)
                                                                    .build();

  public CloudFrontPresignedUrlProviderImpl(@NotNull final TimeService timeService,
                                            @NotNull final AmazonS3Provider amazonS3Provider) {
    super(amazonS3Provider);
    myTimeService = timeService;
  }

  @Nullable
  @Override
  public PresignedUrlWithTtl generateDownloadUrl(@NotNull String objectKey,
                                                 @NotNull CloudFrontSettings settings) throws IOException {
    String distribution = settings.getCloudFrontDownloadDistribution();
    if (distribution == null) {
      distribution = settings.getCloudFrontDistribution();
    }
    int urlTtlSeconds = getUrlTtlSeconds(objectKey, settings, true);
    return new PresignedUrlWithTtl(generateUrl(objectKey, settings, Collections.emptyMap(), distribution, urlTtlSeconds), urlTtlSeconds);
  }

  @Nullable
  @Override
  public String generateUploadUrl(@NotNull String objectKey,
                                  @NotNull CloudFrontSettings settings) throws IOException {
    String distribution = settings.getCloudFrontUploadDistribution();
    if (distribution == null) {
      distribution = settings.getCloudFrontDistribution();
    }
    int urlTtlSeconds = getUrlTtlSeconds(objectKey, settings, false);
    return generateUrl(objectKey, settings, Collections.emptyMap(), distribution, urlTtlSeconds);
  }

  @Nullable
  private String generateUrl(@NotNull String objectKey, @NotNull CloudFrontSettings settings, @NotNull Map<String, String> additionalParameters, @NotNull String distribution, int urlTtlSeconds)
    throws IOException {
    try {
      String domain = getDomainName(settings, distribution);
      String publicKeyId = settings.getCloudFrontPublicKeyId();

      String encodedObjectKey = SdkHttpUtils.urlEncodeIgnoreSlashes(objectKey);
      if (jetbrains.buildServer.util.StringUtil.isNotEmpty(domain) && StringUtil.isNotEmpty(publicKeyId)) {
        String resourcePath = CloudFrontUtils.generateResourcePath(CloudFrontUtils.Protocol.https, domain, encodedObjectKey);

        URIBuilder builder = new URIBuilder(resourcePath);

        if (!additionalParameters.isEmpty()) {
          for (Map.Entry<String, String> param : additionalParameters.entrySet()) {
            builder.addParameter(param.getKey(), param.getValue());
          }
        }

        resourcePath = builder.build().toString();

        byte[] privateKeyBytes = settings.getCloudFrontPrivateKey().getBytes(StandardCharsets.UTF_8);
        PrivateKey decodedPrivateKey = Pem.readPrivateKey(new ByteArrayInputStream(privateKeyBytes));

        final CannedSignerRequest request = CannedSignerRequest.builder()
                                                               .resourceUrl(resourcePath)
                                                               .privateKey(decodedPrivateKey)
                                                               .keyPairId(publicKeyId)
                                                               .expirationDate(new Date(myTimeService.now() + urlTtlSeconds * 1000L).toInstant())
                                                               .build();
        return CLOUD_FRONT_UTILITIES.getSignedUrlWithCannedPolicy(request).url();
      }
      return null;
    } catch (CloudFrontException | InvalidKeySpecException | IOException | URISyntaxException e) {
      final Throwable cause = e.getCause();
      final AWSException awsException = cause != null ? new AWSException(cause) : new AWSException(e);
      final String details = awsException.getDetails();
      if (StringUtil.isNotEmpty(details)) {
        final String message = awsException.getMessage() + details;
        LOG.warnAndDebugDetails(message, cause);
      }
      throw new IOException(
        String.format("Failed to create pre-signed URL to artifact '%s' in CloudFront distribution '%s': %s", objectKey, distribution, awsException.getMessage()),
        awsException);
    }
  }

  @Override
  public String generateUploadUrlForPart(@NotNull String objectKey, int nPart, @NotNull String uploadId, @NotNull CloudFrontSettings settings) throws IOException {
    HashMap<String, String> additionalParameters = new HashMap<>();
    additionalParameters.put("uploadId", uploadId);
    additionalParameters.put("partNumber", String.valueOf(nPart));
    String distribution = settings.getCloudFrontUploadDistribution();
    if (distribution == null) {
      distribution = settings.getCloudFrontDistribution();
    }
    int urlTtlSeconds = getUrlTtlSeconds(objectKey, settings, false);
    return generateUrl(objectKey, settings, additionalParameters, distribution, urlTtlSeconds);
  }

  @Nullable
  private String getDomainName(@NotNull CloudFrontSettings settings, String distribution) throws IOException {
    Map<String, String> params = settings.toRawSettings();
    String projectId = settings.getProjectId();

    String name = null;
    try {
      if (distribution != null) {
        name = domainNameCache.get(distribution, () -> getDistribution(distribution, params, projectId));
      }
    } catch (ExecutionException e) {
      Throwable cause = e;
      if (e.getCause() != null) {
        cause = e.getCause();
      }
      throw new IOException("Could not fetch distribution " + distribution + " from CloudFront", cause);
    }
    return name;
  }

  @Nullable
  private String getDistribution(@NotNull String distributionName, @NotNull Map<String, String> params, String projectId) throws CloudFrontException, ConnectionCredentialsException {
    return myAmazonS3Provider.withCloudFrontClient(params, projectId, cloudFrontClient -> {
      return cloudFrontClient.getDistribution(b -> b.id(distributionName))
                             .distribution()
                             .domainName();
    });
  }
}
