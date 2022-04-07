package jetbrains.buildServer.artifacts.s3.settings;

import com.amazonaws.SdkClientException;
import com.amazonaws.auth.PEM;
import com.amazonaws.services.cloudfront.AmazonCloudFront;
import com.amazonaws.services.cloudfront.model.GetPublicKeyRequest;
import com.intellij.openapi.diagnostic.Logger;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAKey;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.serverSide.IOGuard;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.artifacts.s3.cloudfront.CloudFrontConstants.*;

public class CloudFrontPropertiesProcessor implements PropertiesProcessor {
  private static final Logger LOG = Logger.getInstance(CloudFrontPropertiesProcessor.class.getName());

  @Override
  public Collection<InvalidProperty> process(Map<String, String> params) {
    final ArrayList<InvalidProperty> invalids = new ArrayList<>();

    if (StringUtil.isEmptyOrSpaces(S3Util.getCloudFrontUploadDistribution(params))) {
      invalids.add(new InvalidProperty(S3_CLOUDFRONT_UPLOAD_DISTRIBUTION, "CloudFront distribution for upload should not be empty"));
    }

    if (StringUtil.isEmptyOrSpaces(S3Util.getCloudFrontDownloadDistribution(params))) {
      invalids.add(new InvalidProperty(S3_CLOUDFRONT_DOWNLOAD_DISTRIBUTION, "CloudFront distribution for download should not be empty"));
    }

    PrivateKey privateKey = getPrivateKey(params, invalids);
    PublicKey publicKey = getPublicKey(params, invalids);

    if (privateKey != null && publicKey != null) {
      if (!verifyKeyPair(privateKey, publicKey)) {
        String msg = "Private and public keys do not match";
        invalids.add(new InvalidProperty(S3_CLOUDFRONT_PRIVATE_KEY, msg));
        invalids.add(new InvalidProperty(S3_CLOUDFRONT_PUBLIC_KEY_ID, msg));
      }
    }
    return invalids;
  }

  @Nullable
  private PrivateKey getPrivateKey(@NotNull Map<String, String> params,
                                   @NotNull List<InvalidProperty> invalids) {
    String cloudFrontPrivateKey = S3Util.getCloudFrontPrivateKey(params);
    if (cloudFrontPrivateKey == null) {
      invalids.add(new InvalidProperty(S3_CLOUDFRONT_PRIVATE_KEY, "Private key should not be empty"));
      return null;
    }

    PrivateKey privateKey;
    try {
      privateKey = PEM.readPrivateKey(new ByteArrayInputStream(cloudFrontPrivateKey.getBytes(StandardCharsets.UTF_8)));
    } catch (InvalidKeySpecException | IOException | IllegalArgumentException | NullPointerException e) {
      LOG.warnAndDebugDetails("Error while processing private key in CloudFront settings", e);
      invalids.add(new InvalidProperty(S3_CLOUDFRONT_PRIVATE_KEY, "Chosen Private key is not compatible with CloudFront"));
      return null;
    }

    boolean isRsaKey = privateKey.getAlgorithm().equals("RSA");
    boolean isCorrectSize = isRsaKey && ((RSAKey)privateKey).getModulus().bitLength() == 2048;

    if (!isCorrectSize) {
      invalids.add(new InvalidProperty(S3_CLOUDFRONT_PRIVATE_KEY, "Chosen Private key is not compatible with CloudFront"));
      return null;
    }

    return privateKey;
  }

  @Nullable
  private PublicKey getPublicKey(@NotNull Map<String, String> params, @NotNull List<InvalidProperty> invalids) {
    String cloudFrontPublicKeyId = S3Util.getCloudFrontPublicKeyId(params);
    if (cloudFrontPublicKeyId == null) {
      invalids.add(new InvalidProperty(S3_CLOUDFRONT_PUBLIC_KEY_ID, "CloudFront public key should not be empty"));
    }

    if (cloudFrontPublicKeyId != null) {
      return AWSCommonParams.withAWSClients(params, clients -> {
        AmazonCloudFront cloudFrontClient = clients.createCloudFrontClient();

        try {
          byte[] keyBytes = IOGuard.allowNetworkCall(() -> cloudFrontClient.getPublicKey(new GetPublicKeyRequest().withId(cloudFrontPublicKeyId))
                                                                           .getPublicKey()
                                                                           .getPublicKeyConfig()
                                                                           .getEncodedKey()
                                                                           .getBytes(StandardCharsets.UTF_8));

          return PEM.readPublicKey(new ByteArrayInputStream(keyBytes));

        } catch (SdkClientException | InvalidKeySpecException | IOException e) {
          LOG.warnAndDebugDetails("Error while retrieving public key from CloudFront", e);
          invalids.add(new InvalidProperty(S3_CLOUDFRONT_PUBLIC_KEY_ID, e.getMessage()));
          return null;
        }
      });
    }
    return null;
  }

  private boolean verifyKeyPair(@NotNull PrivateKey privateKey, @NotNull PublicKey publicKey) {
    try {
      byte[] testLine = "testLine".getBytes(StandardCharsets.UTF_8);
      java.security.Signature signature = Signature.getInstance("SHA1withRSA");
      signature.initSign(privateKey);
      signature.update(testLine);
      byte[] signatureBytes = signature.sign();

      signature.initVerify(publicKey);
      signature.update(testLine);

      return signature.verify(signatureBytes);
    } catch (SignatureException | InvalidKeyException | NoSuchAlgorithmException e) {
      return false;
    }
  }
}
