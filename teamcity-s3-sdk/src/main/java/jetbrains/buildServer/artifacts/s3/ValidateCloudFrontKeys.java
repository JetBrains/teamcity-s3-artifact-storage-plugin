package jetbrains.buildServer.artifacts.s3;

import com.amazonaws.SdkClientException;
import com.amazonaws.auth.PEM;
import com.amazonaws.services.cloudfront.model.GetPublicKeyRequest;
import com.intellij.openapi.diagnostic.Logger;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Collections;
import java.util.Map;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;
import jetbrains.buildServer.Used;
import jetbrains.buildServer.artifacts.s3.amazonClient.AmazonS3Provider;
import jetbrains.buildServer.artifacts.s3.exceptions.InvalidSettingsException;
import jetbrains.buildServer.serverSide.IOGuard;
import jetbrains.buildServer.serverSide.connections.credentials.ConnectionCredentialsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.artifacts.s3.cloudfront.CloudFrontConstants.S3_CLOUDFRONT_PRIVATE_KEY;
import static jetbrains.buildServer.artifacts.s3.cloudfront.CloudFrontConstants.S3_CLOUDFRONT_PUBLIC_KEY_ID;

public class ValidateCloudFrontKeys extends S3ClientResourceFetcher<ValidateCloudFrontKeys.CfKeysValidationResult> {
  private static final Logger LOG = Logger.getInstance(ValidateCloudFrontKeys.class.getName());

  private final AmazonS3Provider myAmazonS3Provider;

  public ValidateCloudFrontKeys(AmazonS3Provider amazonS3Provider) {
    myAmazonS3Provider = amazonS3Provider;
  }

  @Override
  protected CfKeysValidationResult fetchCurrentValue(Map<String, String> parameters, @NotNull String projectId) {
    return fetchDto(parameters, projectId);
  }

  @Override
  protected CfKeysValidationResult fetchDto(Map<String, String> parameters, @NotNull String projectId) {
    try {
      PublicKey publicKey = getPublicKey(parameters, projectId);
      PrivateKey privateKey = getPrivateKey(parameters);

      if (privateKey != null && publicKey != null) {
        if (!verifyKeyPair(privateKey, publicKey)) {
          return new CfKeysValidationResult("Private and public keys do not match");
        }
      } else {
        return new CfKeysValidationResult("Private and public keys are required");
      }
    } catch (Exception e) {
      return new CfKeysValidationResult(e.getMessage());
    }

    return new CfKeysValidationResult("OK");
  }

  @Nullable
  public static PrivateKey getPrivateKey(@NotNull Map<String, String> params) {
    String cloudFrontPrivateKey = S3Util.getCloudFrontPrivateKey(params);
    if (cloudFrontPrivateKey == null) {
      return null;
    }

    PrivateKey privateKey;
    try {
      privateKey = PEM.readPrivateKey(new ByteArrayInputStream(cloudFrontPrivateKey.getBytes(StandardCharsets.UTF_8)));
    } catch (InvalidKeySpecException | IOException | IllegalArgumentException | NullPointerException e) {
      LOG.warnAndDebugDetails("Error while processing private key in CloudFront settings", e);
      throw new InvalidSettingsException(Collections.singletonMap(S3_CLOUDFRONT_PRIVATE_KEY, "Chosen Private key is not compatible with CloudFront"));
    }

    boolean isRsaKey = privateKey.getAlgorithm().equals("RSA");
    boolean isCorrectSize = isRsaKey && ((RSAKey)privateKey).getModulus().bitLength() == 2048;

    if (!isCorrectSize) {
      throw new InvalidSettingsException(Collections.singletonMap(S3_CLOUDFRONT_PRIVATE_KEY, "Chosen Private key is not compatible with CloudFront"));
    }

    return privateKey;
  }

  @Nullable
  private PublicKey getPublicKey(@NotNull Map<String, String> params, String projectId) throws ConnectionCredentialsException {
    String cloudFrontPublicKeyId = S3Util.getCloudFrontPublicKeyId(params);
    if (cloudFrontPublicKeyId == null) {
      return null;
    }

    return myAmazonS3Provider.withCloudFrontClient(params, projectId, cloudFrontClient -> {
      try {
        byte[] keyBytes = IOGuard.allowNetworkCall(
          () -> cloudFrontClient.getPublicKey(new GetPublicKeyRequest().withId(cloudFrontPublicKeyId))
                                .getPublicKey()
                                .getPublicKeyConfig()
                                .getEncodedKey()
                                .getBytes(StandardCharsets.UTF_8));

        return PEM.readPublicKey(new ByteArrayInputStream(keyBytes));
      } catch (SdkClientException | InvalidKeySpecException | IOException e) {
        LOG.warnAndDebugDetails("Error while retrieving public key from CloudFront", e);
        throw new InvalidSettingsException(Collections.singletonMap(S3_CLOUDFRONT_PUBLIC_KEY_ID, e.getMessage()));
      }
    });
  }

  private boolean verifyKeyPair(@NotNull PrivateKey privateKey, @NotNull PublicKey publicKey) {
    try {
      byte[] testLine = "testLine".getBytes(StandardCharsets.UTF_8);
      Signature signature = Signature.getInstance("SHA1withRSA");
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

  @XmlRootElement(name = "cfKeysValidationResult")
  @XmlAccessorType(XmlAccessType.PROPERTY)
  public static class CfKeysValidationResult {

    private final String myResult;

    @Used("xml-serialization")
    public CfKeysValidationResult() {
      myResult = null;
    }

    public CfKeysValidationResult(@NotNull String result) {
      myResult = result;
    }

    @XmlValue
    public String getValidationResult() {
      return myResult;
    }
  }
}
