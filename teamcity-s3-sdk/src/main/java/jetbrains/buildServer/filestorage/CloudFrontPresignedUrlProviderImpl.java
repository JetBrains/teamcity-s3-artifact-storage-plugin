package jetbrains.buildServer.filestorage;

import com.amazonaws.services.cloudfront.CloudFrontUrlSigner;
import com.amazonaws.services.cloudfront.util.SignerUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.security.spec.InvalidKeySpecException;
import java.util.Date;
import java.util.Map;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.ssh.TeamCitySshKey;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.TimeService;
import jetbrains.buildServer.util.amazon.AWSException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CloudFrontPresignedUrlProviderImpl implements CloudFrontPresignedUrlProvider{
  @NotNull
  private static final Logger LOG = Logger.getInstance(CloudFrontPresignedUrlProviderImpl.class.getName());
  private final TimeService myTimeService;

  public CloudFrontPresignedUrlProviderImpl(@NotNull final TimeService timeService) {
    myTimeService = timeService;
  }

  @NotNull
  @Override
  public String generateDownloadUrl(@NotNull String objectKey, @NotNull TeamCitySshKey privateKey,
                                    @NotNull CloudFrontSettings settings) throws IOException {
    String domain = settings.getCloudFrontDomain();
    String publicKeyId = settings.getCloudFrontPublicKeyId();
    final Path filePath = FileUtil.createTempFile(new File(FileUtil.getTempDirectory()), "pk", ".pem", true).toPath();

    File keyFile = filePath.toFile();
    try {
      FileUtil.writeFileAndReportErrors(keyFile, new String(privateKey.getPrivateKey()));
      return CloudFrontUrlSigner.getSignedURLWithCannedPolicy(SignerUtils.Protocol.https, domain, keyFile, objectKey, publicKeyId,
                                                              new Date(myTimeService.now() + settings.getUrlTtlSeconds() * 1000L));
    } catch (InvalidKeySpecException e) {
      final Throwable cause = e.getCause();
      final AWSException awsException = cause != null ? new AWSException(cause) : new AWSException(e);
      final String details = awsException.getDetails();
      if (StringUtil.isNotEmpty(details)) {
        final String message = awsException.getMessage() + details;
        LOG.warnAndDebugDetails(message, cause);
      }
      throw new IOException(String.format("Failed to create pre-signed URL to artifact '%s' in CloudFront domain '%s': %s", objectKey, domain, awsException.getMessage()), awsException);
    }finally {
      FileUtil.delete(keyFile);
    }
  }

  @NotNull
  @Override
  public CloudFrontSettings settings(@NotNull Map<String, String> rawSettings) {
    if (S3Util.getBucketName(rawSettings) == null) {
      throw new IllegalArgumentException("Settings don't contain bucket name");
    }
    return new CloudFrontSettingsImpl(rawSettings);
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
    public String getCloudFrontDomain() {
      return S3Util.getCloudFrontDomain(mySettings);
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
