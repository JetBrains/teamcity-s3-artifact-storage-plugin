package jetbrains.buildServer.artifacts.s3.resolve;

import com.amazonaws.services.s3.transfer.Download;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.agent.CurrentBuildTracker;
import jetbrains.buildServer.agent.artifacts.AgentArtifactHelper;
import jetbrains.buildServer.agent.artifacts.ArtifactAccessorBase;
import jetbrains.buildServer.artifacts.ArtifactData;
import jetbrains.buildServer.artifacts.ArtifactListData;
import jetbrains.buildServer.artifacts.ResolvingFailedException;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.amazon.AWSException;
import jetbrains.buildServer.util.amazon.S3Util;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static jetbrains.buildServer.artifacts.s3.S3Util.*;

/**
 * Created by Nikita.Skvortsov
 * date: 26.01.2017.
 */
public class S3ArtifactAccessor extends ArtifactAccessorBase {

  private final static Logger LOG = Logger.getInstance(S3ArtifactAccessor.class.getName());

  @NotNull private final Map<String, String> myParams;
  @NotNull private final CurrentBuildTracker myCurrentBuildTracker;

  @NotNull private final AtomicReference<S3Util.TransferManagerInterruptHook> myInterruptHook = new AtomicReference<S3Util.TransferManagerInterruptHook>();


  public S3ArtifactAccessor(@NotNull Map<String, String> params,
                            @NotNull CurrentBuildTracker currentBuildTracker,
                            @NotNull AgentArtifactHelper helper) {
    super(helper);
    myParams = validateParameters(params);
    myCurrentBuildTracker = currentBuildTracker;
  }

  @Override
  public void downloadArtifacts(@NotNull final String sourceExternalId, final long buildId, @NotNull final Map<String, File> sourceToFiles) {
    try {
      final ArtifactListData artifactsInfo = myHelper.getArtifactList(sourceExternalId, buildId);
      if (artifactsInfo == null) {
        throw new ResolvingFailedException("Failed to download artifacts for build " + buildId + ": no S3 artifacts info available");
      }

      final String bucketName = getBucketName(myParams);
      final String pathPrefix = getPathPrefix(artifactsInfo);
      final Set<String> externalArtifacts = getExternalArtifactPaths(artifactsInfo);

      S3Util.withTransferManager(myParams, new S3Util.InterruptAwareWithTransferManager() {
        @NotNull
        @Override
        public Collection<Download> run(@NotNull final TransferManager transferManager) throws Throwable {
          return CollectionsUtil.convertCollection(sourceToFiles.entrySet(), new Converter<Download, Map.Entry<String, File>>() {
            @Override
            public Download createFrom(@NotNull Map.Entry<String, File> entry) {
              final String sourcePath = entry.getKey();

              if (externalArtifacts.contains(sourcePath)) {
                return transferManager.download(bucketName, pathPrefix + sourcePath, entry.getValue());
              }
              throw new ResolvingFailedException("Failed to download [" + sourcePath + "] from [" + sourceExternalId + ":" + buildId + "]: no S3 artifact info found");
            }
          });
        }

        @Override
        public void setInterruptHook(@NotNull S3Util.TransferManagerInterruptHook transferManagerInterruptHook) {
          myInterruptHook.set(transferManagerInterruptHook);
        }
      });
    } catch (ResolvingFailedException e) {
      throw e;
    } catch (Throwable t) {
      final AWSException awsException = new AWSException(t);
      if (StringUtil.isNotEmpty(awsException.getDetails())) {
        LOG.warn(awsException.getDetails());
        myCurrentBuildTracker.getCurrentBuild().getBuildLogger().error(awsException.getDetails());
      }
      LOG.warnAndDebugDetails("Failed to download artifacts for build " + buildId, awsException);
      throw new ResolvingFailedException(awsException.getMessage(), awsException);
    } finally {
      myInterruptHook.set(null);
    }
  }

  @Override
  public void interrupt() {
    final S3Util.TransferManagerInterruptHook hook = myInterruptHook.getAndSet(null);
    if (hook == null) {
      LOG.warn("Failed to interrupt artifacts download: no interrupt hook initialized");
      return;
    }

    try {
      hook.interrupt();
    } catch (Throwable t) {
      final AWSException awsException = new AWSException(t);
      if (StringUtil.isNotEmpty(awsException.getDetails())) {
        LOG.warn(awsException.getDetails());
      }
      LOG.warnAndDebugDetails("Exception while interrupting artifacts download", t);
    }
  }

  @NotNull
  private Set<String> getExternalArtifactPaths(@NotNull ArtifactListData artifactsInfo) {
    final Set<String> res = new HashSet<String>();
    for (ArtifactData a : artifactsInfo.getArtifactList()) {
      res.add(a.getPath());
    }
    return res;
  }
}
