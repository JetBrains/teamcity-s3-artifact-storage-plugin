package jetbrains.buildServer.artifacts.s3;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.artifacts.ArtifactData;
import jetbrains.buildServer.artifacts.s3.util.ParamUtil;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.artifacts.ArtifactContentProvider;
import jetbrains.buildServer.serverSide.artifacts.StoredBuildArtifactInfo;
import jetbrains.buildServer.util.StringUtil;
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
  private final ServerPaths myServerPaths;

  public S3ArtifactContentProvider(@NotNull ServerPaths serverPaths) {
    myServerPaths = serverPaths;
  }

  @NotNull
  @Override
  public String getType() {
    return S3Constants.S3_STORAGE_TYPE;
  }

  @NotNull
  @Override
  public InputStream getContent(@NotNull StoredBuildArtifactInfo storedBuildArtifactInfo) throws IOException {
    final Map<String, String> params;
    final ArtifactData artifactData = storedBuildArtifactInfo.getArtifactData();
    if (artifactData == null) {
      throw new IOException("Invalid artifact data: S3 object path property is not set");
    }

    final String artifactPath = artifactData.getPath();
    try {
      params = S3Util.validateParameters(storedBuildArtifactInfo.getStorageSettings());
    } catch (IllegalArgumentException e) {
      throw new IOException("Failed to get artifact " + artifactPath + " content: Invalid storage settings " + e.getMessage(), e);
    }

    final String bucketName = S3Util.getBucketName(params);
    final String key = S3Util.getPathPrefix(storedBuildArtifactInfo.getCommonProperties()) + artifactPath;

    try {
      return S3Util.withS3Client(
        ParamUtil.putSslValues(myServerPaths, params),
        client -> client.getObject(bucketName, key).getObjectContent()
      );
    } catch (Throwable t) {
      final AWSException awsException = new AWSException(t);
      final String details = awsException.getDetails();
      if (StringUtil.isNotEmpty(details)) {
        final String message = awsException.getMessage() + details;
        LOG.warn(message);
      }
      throw new IOException(String.format(
        "Failed to get artifact '%s' content in bucket '%s': %s",
        artifactPath, bucketName, awsException.getMessage()
      ), awsException);
    }
  }
}
