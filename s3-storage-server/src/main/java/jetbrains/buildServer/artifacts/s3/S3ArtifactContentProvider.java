package jetbrains.buildServer.artifacts.s3;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.artifacts.ArtifactData;
import jetbrains.buildServer.serverSide.artifacts.ArtifactContentProvider;
import jetbrains.buildServer.serverSide.artifacts.StoredBuildArtifactInfo;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import jetbrains.buildServer.util.amazon.AWSException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * @author vbedrosova
 */
public class S3ArtifactContentProvider implements ArtifactContentProvider {

  private final static Logger LOG = Logger.getInstance(S3ArtifactContentProvider.class.getName());

  @NotNull
  @Override
  public String getType() {
    return S3Constants.S3_STORAGE_TYPE;
  }

  @NotNull
  @Override
  public InputStream getContent(@NotNull StoredBuildArtifactInfo artifactInfo) throws IOException {
    final ArtifactData artifact = artifactInfo.getArtifactData();
    final Map<String, String> storageSettings = artifactInfo.getStorageSettings();
    final Map<String, String> commonProperties = artifactInfo.getCommonProperties();
    final Map<String, String> params;
    try {
      params = S3Util.validateParameters(storageSettings);
    } catch (IllegalArgumentException e) {
      throw new IOException("Failed to get artifact " + artifact + " content from S3: " + e.getMessage(), e);
    }

    final String bucketName = S3Util.getBucketName(storageSettings);
    final String key = S3Util.getPathPrefix(commonProperties) + artifact.getPath();

    try {
      return AWSCommonParams.withAWSClients(params, awsClients -> awsClients.createS3Client().getObject(bucketName, key).getObjectContent());
    } catch (Throwable t) {
      final AWSException awsException = new AWSException(t);

      final String details = awsException.getDetails();
      if (StringUtil.isNotEmpty(details)) {
        LOG.warn(details);
      }

      throw new IOException("Failed to get artifact " + artifact + " content from S3 bucket " + bucketName + ": " + awsException.getMessage(), awsException);
    }
  }
}
