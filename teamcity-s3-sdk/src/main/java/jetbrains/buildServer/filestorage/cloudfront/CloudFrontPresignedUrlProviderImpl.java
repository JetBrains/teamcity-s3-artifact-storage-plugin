package jetbrains.buildServer.filestorage.cloudfront;

import com.amazonaws.auth.PEM;
import com.amazonaws.services.cloudfront.AmazonCloudFront;
import com.amazonaws.services.cloudfront.CloudFrontUrlSigner;
import com.amazonaws.services.cloudfront.model.AmazonCloudFrontException;
import com.amazonaws.services.cloudfront.model.Distribution;
import com.amazonaws.services.cloudfront.model.GetDistributionRequest;
import com.amazonaws.services.cloudfront.util.SignerUtils;
import com.amazonaws.util.SdkHttpUtils;
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
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.util.TimeService;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import jetbrains.buildServer.util.amazon.AWSException;
import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CloudFrontPresignedUrlProviderImpl implements CloudFrontPresignedUrlProvider {
  @NotNull
  private static final Logger LOG = Logger.getInstance(CloudFrontPresignedUrlProviderImpl.class.getName());

  private static final String PARAM = "%s=%s";

  private final TimeService myTimeService;

  public CloudFrontPresignedUrlProviderImpl(@NotNull final TimeService timeService) {
    myTimeService = timeService;
  }

  @Nullable
  @Override
  public String generateUrl(@NotNull String objectKey,
                            @NotNull CloudFrontSettings settings) throws IOException {
    return generateUrl(objectKey, settings, Collections.emptyMap());
  }

  @Nullable
  private String generateUrl(@NotNull String objectKey, @NotNull CloudFrontSettings settings, @NotNull Map<String, String> additionalParameters) throws IOException {
    try {
      Distribution distribution = getDistribution(settings);
      String domain = distribution.getDomainName();
      String publicKeyId = settings.getCloudFrontPublicKeyId();

      String encodedObjectKey = SdkHttpUtils.urlEncode(objectKey, true);
      if (jetbrains.buildServer.util.StringUtil.isNotEmpty(domain) && StringUtil.isNotEmpty(publicKeyId)) {
        String resourcePath = SignerUtils.generateResourcePath(SignerUtils.Protocol.https, domain, encodedObjectKey);

        if (!additionalParameters.isEmpty()) {
          URIBuilder builder = new URIBuilder(resourcePath);
          for (Map.Entry<String, String> param : additionalParameters.entrySet()) {
            builder.addParameter(param.getKey(), param.getValue());
          }

          resourcePath = builder.build().toString();
        }

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
      throw new IOException(String.format("Failed to create pre-signed URL to artifact '%s' in CloudFront distribution '%s': %s", objectKey, settings.getCloudFrontDistribution(),
                                          awsException.getMessage()),
                            awsException);
    }
  }

  @Override
  public String generateUrlForPart(@NotNull String objectKey, int nPart, @NotNull String uploadId, @NotNull CloudFrontSettings settings) throws IOException {
    HashMap<String, String> additionalParameters = new HashMap<>();
    additionalParameters.put("uploadId", uploadId);
    additionalParameters.put("partNumber", String.valueOf(nPart));
    return generateUrl(objectKey, settings, additionalParameters);
  }

  @NotNull
  private Distribution getDistribution(@NotNull CloudFrontSettings settings) throws AmazonCloudFrontException {
    Map<String, String> params = ((CloudFrontSettingsImpl)settings).toRawSettings();
    return AWSCommonParams.withAWSClients(params, clients -> {
      AmazonCloudFront cloudFrontClient = clients.createCloudFrontClient();
      String selectedDistribution = S3Util.getCloudFrontDistribution(params);

      return cloudFrontClient.getDistribution(new GetDistributionRequest(selectedDistribution))
                             .getDistribution();
    });
  }


}
