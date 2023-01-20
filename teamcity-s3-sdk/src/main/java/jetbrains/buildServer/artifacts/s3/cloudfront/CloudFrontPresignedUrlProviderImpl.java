package jetbrains.buildServer.artifacts.s3.cloudfront;

import com.amazonaws.auth.PEM;
import com.amazonaws.services.cloudfront.AmazonCloudFront;
import com.amazonaws.services.cloudfront.CloudFrontUrlSigner;
import com.amazonaws.services.cloudfront.model.AmazonCloudFrontException;
import com.amazonaws.services.cloudfront.model.GetDistributionRequest;
import com.amazonaws.services.cloudfront.util.SignerUtils;
import com.amazonaws.util.SdkHttpUtils;
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
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.TimeService;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import jetbrains.buildServer.util.amazon.AWSException;
import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.artifacts.s3.cloudfront.CloudFrontConstants.S3_CLOUDFRONT_DOMAIN_NAME_CACHE_EXPIRATION_HOURS;

public class CloudFrontPresignedUrlProviderImpl implements CloudFrontPresignedUrlProvider {
  @NotNull
  private static final Logger LOG = Logger.getInstance(CloudFrontPresignedUrlProviderImpl.class.getName());

  @NotNull
  private final TimeService myTimeService;

  @NotNull
  private final Cache<String, String> domainNameCache = CacheBuilder.newBuilder()
                                                                    .expireAfterWrite(TeamCityProperties.getInteger(S3_CLOUDFRONT_DOMAIN_NAME_CACHE_EXPIRATION_HOURS, 1),
                                                                                      TimeUnit.HOURS)
                                                                    .build();

  public CloudFrontPresignedUrlProviderImpl(@NotNull final TimeService timeService) {
    myTimeService = timeService;
  }

  @Nullable
  @Override
  public String generateDownloadUrl(@NotNull String objectKey,
                                    @NotNull CloudFrontSettings settings) throws IOException {
    String distribution = settings.getCloudFrontDownloadDistribution();
    if (distribution == null) {
      distribution = settings.getCloudFrontDistribution();
    }
    return generateUrl(objectKey, settings, Collections.emptyMap(), distribution);
  }

  @Nullable
  @Override
  public String generateUploadUrl(@NotNull String objectKey,
                                  @NotNull CloudFrontSettings settings) throws IOException {
    String distribution = settings.getCloudFrontUploadDistribution();
    if (distribution == null) {
      distribution = settings.getCloudFrontDistribution();
    }
    return generateUrl(objectKey, settings, Collections.emptyMap(), distribution);
  }

  @Nullable
  private String generateUrl(@NotNull String objectKey, @NotNull CloudFrontSettings settings, @NotNull Map<String, String> additionalParameters, @NotNull String distribution)
    throws IOException {
    try {
      String domain = getDomainName(settings, distribution);
      String publicKeyId = settings.getCloudFrontPublicKeyId();

      String encodedObjectKey = SdkHttpUtils.urlEncode(objectKey, true);
      if (jetbrains.buildServer.util.StringUtil.isNotEmpty(domain) && StringUtil.isNotEmpty(publicKeyId)) {
        String resourcePath = SignerUtils.generateResourcePath(SignerUtils.Protocol.https, domain, encodedObjectKey);

        URIBuilder builder = new URIBuilder(resourcePath);

        if (!additionalParameters.isEmpty()) {
          for (Map.Entry<String, String> param : additionalParameters.entrySet()) {
            builder.addParameter(param.getKey(), param.getValue());
          }
        }

        resourcePath = builder.build().toString();

        byte[] privateKeyBytes = settings.getCloudFrontPrivateKey().getBytes(StandardCharsets.UTF_8);
        PrivateKey decodedPrivateKey = PEM.readPrivateKey(new ByteArrayInputStream(privateKeyBytes));

        return CloudFrontUrlSigner.getSignedURLWithCannedPolicy(resourcePath, publicKeyId, decodedPrivateKey,
                                                                new Date(myTimeService.now() + settings.getUrlTtlSeconds() * 1000L));
      }
      return null;
    } catch (AmazonCloudFrontException | InvalidKeySpecException | IOException | URISyntaxException e) {
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
    return generateUrl(objectKey, settings, additionalParameters, distribution);
  }

  @Nullable
  private String getDomainName(@NotNull CloudFrontSettings settings, String distribution) throws IOException {
    Map<String, String> params = settings.toRawSettings();

    String name = null;
    try {
      if (distribution != null) {
        name = domainNameCache.get(distribution, () -> getDistribution(distribution, params));
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
  private String getDistribution(@NotNull String distributionName, @NotNull Map<String, String> params) throws AmazonCloudFrontException {
    return AWSCommonParams.withAWSClients(params, clients -> {
      AmazonCloudFront cloudFrontClient = clients.createCloudFrontClient();

      return cloudFrontClient.getDistribution(new GetDistributionRequest(distributionName))
                             .getDistribution()
                             .getDomainName();
    });
  }


}
