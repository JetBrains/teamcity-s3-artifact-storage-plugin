package jetbrains.buildServer.filestorage;

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
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Date;
import java.util.Map;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.ssh.TeamCitySshKey;
import jetbrains.buildServer.util.TimeService;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import jetbrains.buildServer.util.amazon.AWSException;
import jetbrains.buildServer.util.jsch.JSchConfigInitializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CloudFrontPresignedUrlProviderImpl implements CloudFrontPresignedUrlProvider {
  @NotNull
  private static final Logger LOG = Logger.getInstance(CloudFrontPresignedUrlProviderImpl.class.getName());
  private final TimeService myTimeService;

  public CloudFrontPresignedUrlProviderImpl(@NotNull final TimeService timeService) {
    myTimeService = timeService;
    JSchConfigInitializer.initJSchConfig(JSch.class);
  }

  @Nullable
  @Override
  public String generateDownloadUrl(@NotNull String objectKey, @NotNull TeamCitySshKey privateKey,
                                    @NotNull CloudFrontSettings settings) throws IOException {
    try {
      Distribution distribution = getDistribution(settings);
      String domain = distribution.getDomainName();
      String publicKeyId = settings.getCloudFrontPublicKeyId();

      String encodedObjectKey = SdkHttpUtils.urlEncode(objectKey, true);
      if (jetbrains.buildServer.util.StringUtil.isNotEmpty(domain) && StringUtil.isNotEmpty(publicKeyId)) {
        String resourcePath = SignerUtils.generateResourcePath(SignerUtils.Protocol.https, domain, encodedObjectKey);

        PrivateKey decodedPrivateKey = decodePrivateKey(privateKey, settings);

        return CloudFrontUrlSigner.getSignedURLWithCannedPolicy(resourcePath, publicKeyId, decodedPrivateKey,
          new Date(myTimeService.now() + settings.getUrlTtlSeconds() * 1000L));
      }
      return null;
    } catch (InvalidKeySpecException | AmazonCloudFrontException | IOException | JSchException e) {
      final Throwable cause = e.getCause();
      final AWSException awsException = cause != null ? new AWSException(cause) : new AWSException(e);
      final String details = awsException.getDetails();
      if (StringUtil.isNotEmpty(details)) {
        final String message = awsException.getMessage() + details;
        LOG.warnAndDebugDetails(message, cause);
      }
      throw new IOException(String.format("Failed to create pre-signed URL to artifact '%s' in CloudFront distribution '%s': %s", objectKey, settings.getCloudFrontDistribution(), awsException.getMessage()),
        awsException);
    }
  }

  @NotNull
  private PrivateKey decodePrivateKey(@NotNull TeamCitySshKey privateKey, @NotNull CloudFrontSettings settings) throws JSchException, InvalidKeySpecException, IOException {
    JSch jsch = new JSch();
    KeyPair keyPair = KeyPair.load(jsch, privateKey.getPrivateKey(), null);

    byte[] key;
    if (keyPair.isEncrypted() && settings.getCloudFrontPrivateKeyPassphrase() != null) {
      keyPair.decrypt(settings.getCloudFrontPrivateKeyPassphrase());
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      keyPair.writePrivateKey(outputStream, null);
      key = outputStream.toByteArray();
    } else {
      key = privateKey.getPrivateKey();
    }

    return PEM.readPrivateKey(new ByteArrayInputStream(key));
  }

  @NotNull
  @Override
  public CloudFrontSettings settings(@NotNull Map<String, String> rawSettings) {
    if (S3Util.getBucketName(rawSettings) == null) {
      throw new IllegalArgumentException("Settings don't contain bucket name");
    }
    return new CloudFrontSettingsImpl(rawSettings);
  }

  private Distribution getDistribution(CloudFrontSettings settings) throws AmazonCloudFrontException {
    Map<String, String> params = ((CloudFrontSettingsImpl) settings).getSettings();
    return AWSCommonParams.withAWSClients(params, clients -> {
      AmazonCloudFront cloudFrontClient = clients.createCloudFrontClient();
      String selectedDistribution = S3Util.getCloudFrontDistribution(params);

      return cloudFrontClient.getDistribution(new GetDistributionRequest(selectedDistribution))
        .getDistribution();
    });
  }


  private static class CloudFrontSettingsImpl implements CloudFrontSettings {
    @NotNull
    private final Map<String, String> mySettings;

    private CloudFrontSettingsImpl(@NotNull final Map<String, String> params) {
      mySettings = params;
    }

    @NotNull
    private Map<String, String> getSettings() {
      return mySettings;
    }

    @Override
    public boolean getCloudFrontEnabled() {
      return S3Util.getCloudFrontEnabled(mySettings);
    }

    @Nullable
    @Override
    public String getCloudFrontDistribution() {
      return S3Util.getCloudFrontDistribution(mySettings);
    }

    @Nullable
    @Override
    public String getCloudFrontPublicKeyId() {
      return S3Util.getCloudFrontPublicKeyId(mySettings);
    }

    @NotNull
    @Override
    public String getCloudFrontPrivateKey() {
      return S3Util.getCloudFrontPrivateKey(mySettings);
    }

    @Nullable
    @Override
    public String getCloudFrontPrivateKeyPassphrase() {
      return S3Util.getCloudFrontPrivateKeyPassphrase(mySettings);
    }

    @NotNull
    @Override
    public String getBucketRegion() {
      return S3Util.getBucketRegion(mySettings);
    }

    @NotNull
    @Override
    public String getBucketName() {
      return S3Util.getBucketName(mySettings);
    }

    @Override
    public int getUrlTtlSeconds() {
      return S3Util.getUrlTtlSeconds(mySettings);
    }
  }
}
