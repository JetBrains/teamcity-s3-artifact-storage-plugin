package jetbrains.buildServer.artifacts.s3.resolve;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import jetbrains.buildServer.agent.artifacts.AgentExternalArtifactHelper;
import jetbrains.buildServer.artifacts.ArtifactAccessor;
import jetbrains.buildServer.artifacts.ExternalArtifact;
import jetbrains.buildServer.artifacts.ResolvingFailedException;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.amazon.AWSClients;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import jetbrains.buildServer.util.amazon.AWSException;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Nikita.Skvortsov
 * date: 26.01.2017.
 */
public class S3ArtifactAccessor implements ArtifactAccessor {

  private final static Logger LOG = Logger.getLogger(S3ArtifactAccessor.class);

  @NotNull private final Map<String, String> myParams;
  @NotNull private final AgentExternalArtifactHelper myHelper;

  private volatile boolean isInterrupted;


  public S3ArtifactAccessor(@NotNull Map<String, String> params,
                            @NotNull AgentExternalArtifactHelper helper) {
    myParams = params;
    myHelper = helper;
  }

  @NotNull
  @Override
  public Collection<String> getArtifactSourcePathList(@NotNull final String sourceExternalId, final long buildId) {
    return getExternalArtifacts(sourceExternalId, buildId).keySet();
  }

  @Override
  public void downloadArtifacts(@NotNull final String sourceExternalId, final long buildId, @NotNull final Map<String, File> sourceToFiles) {
    try {
      AWSCommonParams.withAWSClients(myParams, new AWSCommonParams.WithAWSClients<Void, Throwable>() {
        @Nullable
        @Override
        public Void run(@NotNull AWSClients awsClients) throws Throwable {
          final Map<String, ExternalArtifact> externalArtifacts = getExternalArtifacts(sourceExternalId, buildId);
          final AmazonS3Client s3Client = awsClients.createS3Client();

          for (Map.Entry<String, File> entry : sourceToFiles.entrySet()) {
            final String sourcePath = entry.getKey();
            final File target = entry.getValue();

            final ExternalArtifact externalArtifact = externalArtifacts.get(sourcePath);
            if (externalArtifact == null || externalArtifact.getUrl() == null) {
              throw new ResolvingFailedException("Failed to download [" + sourcePath + "] from [" + sourceExternalId + ":" + buildId + "]");
            }
            downloadObject(s3Client, externalArtifact.getProperties(), target);
          }

          return null;
        }
      });
    } catch (ResolvingFailedException e) {
      throw e;
    } catch (IOException e) {
      throw new ResolvingFailedException(e);
    } catch (Throwable t) {
      final AWSException awsException = new AWSException(t);
      if (StringUtil.isNotEmpty(awsException.getDetails())) {
        LOG.info(awsException.getDetails());
      }
      throw new ResolvingFailedException(awsException.getMessage(), awsException);
    }
  }

  @NotNull
  private Map<String, ExternalArtifact> getExternalArtifacts(@NotNull final String sourceExternalId, final long buildId) {
    final Map<String, ExternalArtifact> res = new HashMap<String, ExternalArtifact>();
    final Collection<ExternalArtifact> externalArtifacts = myHelper.getExternalArtifactsInfo(sourceExternalId, buildId);
    for (ExternalArtifact externalArtifact : externalArtifacts) {
      if (externalArtifact.getProperties().containsKey(S3Constants.S3_KEY_ATTR)) {
        res.put(externalArtifact.getPath(), externalArtifact);
      }
    }
    return res;
  }

  private void downloadObject(@NotNull AmazonS3Client s3Client, @NotNull final Map<String, String> properties, @NotNull File file) throws IOException {
    try {
      final GetObjectRequest request = new GetObjectRequest(properties.get(S3Constants.S3_BUCKET_ATTR),
                                                            properties.get(S3Constants.S3_KEY_ATTR));
      final S3Object object = s3Client.getObject(request);
      file.getParentFile().mkdirs();
      file.createNewFile();

      S3ObjectInputStream is = null;
      FileOutputStream os = null;
      try {
        is = object.getObjectContent();
        os = new FileOutputStream(file);
        final byte[] buffer = new byte[1024 * 1024];
        int count;
        while ((count = is.read(buffer)) > 0) {
          if (isInterrupted) {
            break;
          }
          os.write(buffer, 0, count);
        }
      } finally {
        FileUtil.close(is);
        FileUtil.close(os);
      }

    } catch (MalformedURLException e) {
      throw new IOException(e);
    } finally {
      if (isInterrupted) {
        FileUtil.delete(file);
      }
    }
  }

  @Override
  public void interrupt() {
    isInterrupted = true;
  }
}
