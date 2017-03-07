package jetbrains.buildServer.artifacts.s3.resolve;

import com.amazonaws.event.ProgressEvent;
import com.amazonaws.event.SyncProgressListener;
import com.amazonaws.services.s3.transfer.Download;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.agent.artifacts.AgentExternalArtifactHelper;
import jetbrains.buildServer.artifacts.ArtifactAccessor;
import jetbrains.buildServer.artifacts.ExternalArtifact;
import jetbrains.buildServer.artifacts.ResolvingFailedException;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.amazon.AWSException;
import jetbrains.buildServer.util.amazon.S3Util;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by Nikita.Skvortsov
 * date: 26.01.2017.
 */
public class S3ArtifactAccessor implements ArtifactAccessor {

  private final static Logger LOG = Logger.getInstance(S3ArtifactAccessor.class.getName());

  @NotNull private final Map<String, String> myParams;
  @NotNull private final AgentExternalArtifactHelper myHelper;

  private final AtomicReference<Boolean> isInterrupted = new AtomicReference<Boolean>(false);


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
      final Map<String, ExternalArtifact> externalArtifacts = getExternalArtifacts(sourceExternalId, buildId);
      S3Util.withTransferManager(myParams, new S3Util.WithTransferManager<Download, Throwable>() {
        @NotNull
        @Override
        public Collection<Download> run(@NotNull final TransferManager transferManager) throws Throwable {
          return CollectionsUtil.convertCollection(sourceToFiles.entrySet(), new Converter<Download, Map.Entry<String, File>>() {
            @Override
            public Download createFrom(@NotNull Map.Entry<String, File> entry) {
              final String sourcePath = entry.getKey();
              final File target = entry.getValue();

              final ExternalArtifact externalArtifact = externalArtifacts.get(sourcePath);
              if (externalArtifact == null || externalArtifact.getUrl() == null) {
                throw new ResolvingFailedException("Failed to download [" + sourcePath + "] from [" + sourceExternalId + ":" + buildId + "]");
              }
              final Map<String, String> properties = externalArtifact.getProperties();
              final Download download = transferManager.download(properties.get(S3Constants.S3_BUCKET_ATTR), properties.get(S3Constants.S3_KEY_ATTR), target);
              download.addProgressListener(new SyncProgressListener() {
                @Override
                public void progressChanged(ProgressEvent progressEvent) {
                  if (isInterrupted.get()) {
                    try {
                      download.abort();
                    } catch (Throwable t) {
                      final AWSException awsException = new AWSException(t);
                      if (StringUtil.isNotEmpty(awsException.getDetails())) {
                        LOG.info(awsException.getDetails());
                      }
                      LOG.warnAndDebugDetails("Exception while interrupting artifacts download for build " + buildId, t);
                    }
                  }
                }
              });
              return download;
            }
          });
        }
      });
    } catch (ResolvingFailedException e) {
      throw e;
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

  @Override
  public void interrupt() {
    isInterrupted.set(true);
  }
}
