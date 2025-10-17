

package jetbrains.buildServer.artifacts.s3.publish;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import jetbrains.buildServer.artifacts.s3.FileUploadInfo;
import jetbrains.buildServer.artifacts.s3.S3Configuration;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.artifacts.s3.exceptions.FileUploadFailedException;
import jetbrains.buildServer.artifacts.s3.exceptions.InvalidSettingsException;
import jetbrains.buildServer.artifacts.s3.publish.logger.S3UploadLogger;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import jetbrains.buildServer.util.amazon.AWSException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.FileUpload;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;
import software.amazon.awssdk.transfer.s3.progress.TransferListener;

public class S3RegularFileUploader extends S3FileUploader {
  @NotNull
  private static final Logger LOG = Logger.getInstance(S3RegularFileUploader.class.getName());
  private boolean isDestinationPrepared = false;

  public S3RegularFileUploader(@NotNull final S3Configuration s3Configuration, @NotNull final S3UploadLogger logger) {
    super(s3Configuration, logger);
  }

  @Override
  @Nullable
  public Collection<UploadStatistics> upload(@NotNull final Map<File, String> filesToUpload,
                                             @NotNull final Supplier<String> interrupter,
                                             Consumer<FileUploadInfo> uploadInfoConsumer)
    throws InvalidSettingsException {
    final String bucketName = myS3Configuration.getBucketName();

    try {
      prepareDestination(bucketName, myS3Configuration.getSettingsMap());
      LOG.debug(() -> "Publishing artifacts using S3 configuration " + myS3Configuration);

      S3Util.withTransferManagerCorrectingRegion(myS3Configuration.getSettingsMap(), transferManager ->
        filesToUpload.entrySet()
                     .stream()
                     .map(entry -> createRequest(myS3Configuration.getPathPrefix(), bucketName, uploadInfoConsumer, new Pair<>(entry.getValue(), entry.getKey())))
                     .filter(Objects::nonNull)
                     .map(request -> doUpload(transferManager, request))
                     .collect(Collectors.toList()), myS3Configuration.getAdvancedConfiguration());
    } catch (Throwable t) {
      for (Throwable e: t.getSuppressed()) {
        LOG.infoAndDebugDetails("Got exception while waiting for upload completion", e);
        myLogger.info("Got error while waiting for async artifact upload " + e.getMessage());
      }
      final AWSException awsException = new AWSException(t);
      final String details = awsException.getDetails();

      if (StringUtil.isNotEmpty(details)) {
        final String message = awsException.getMessage() + details;
        LOG.warn(message);
        myLogger.error(message);
      }

      throw new FileUploadFailedException(awsException.getMessage(), false, awsException);
    }
    return null;
  }

  private FileUpload doUpload(@NotNull final S3TransferManager transferManager,
                              @NotNull final UploadFileRequest request) {
    File file = request.source().toFile();
    try {
      return transferManager.uploadFile(request);
    } catch (SdkException e) {
      myLogger.warn("Artifact upload " + file.getName() + " with key " + request.putObjectRequest().key() + " to " + request.putObjectRequest().bucket() + " failed with error " + e.getMessage());
      throw e;
    }
  }

  @Nullable
  private UploadFileRequest createRequest(@NotNull final String pathPrefix,
                                      @NotNull final String bucketName,
                                      @NotNull final Consumer<FileUploadInfo> uploadConsumer,
                                      @NotNull final Pair<String, File> fileWithPath) {
    final File file = fileWithPath.getSecond();
    if (!file.exists()) {
      myLogger.warn("Artifact \"" + file.getAbsolutePath() + "\" does not exist and will not be published to the server");
      return null;
    }
    final String artifactPath = S3Util.normalizeArtifactPath(fileWithPath.getFirst(), file);
    final String objectKey = pathPrefix + artifactPath;

    uploadConsumer.accept(new FileUploadInfo(artifactPath, file.getAbsolutePath(), file.length(), null));

    TransferListener loggingTransferListener = new TransferListener() {
      final AtomicLong fileSize = new AtomicLong(file.length());
      final AtomicInteger reportCounter = new AtomicInteger(0);

      @Override
      public void bytesTransferred(Context.BytesTransferred context) {
        final int percentage = 100 - (int)Math.round((fileSize.getAndAdd(-context.progressSnapshot().transferredBytes()) * 100.) / file.length());
        if (percentage >= reportCounter.get() + 10) {
          myLogger.debug("S3 Multipart Uploading [" + file.getName() + "] " + percentage + "%");
          reportCounter.set(percentage);
        }
      }
    };

    return UploadFileRequest.builder()
                            .putObjectRequest(b -> b
                              .bucket(bucketName)
                              .key(objectKey)
                              .contentType(S3Util.getContentType(file))
                              .acl(myS3Configuration.getAcl())
                            )
                            .addTransferListener(loggingTransferListener)
                            .source(file)
                            .build();
  }

  private void prepareDestination(final String bucketName,
                                  final Map<String, String> params) throws Throwable {
    if (isDestinationPrepared) return;
    try {
      if ("US".equals(params.get(AWSCommonParams.REGION_NAME_PARAM))) {
        params.put(AWSCommonParams.REGION_NAME_PARAM, "us-east-1");
      }
    } catch (Exception e) {
      LOG.infoAndDebugDetails("Attempt to correct aws region from US to us-east-1 failed", e);
    }

    S3Util.withS3ClientShuttingDownImmediately(params, (S3Util.WithS3<Void, Throwable>)s3Client -> {
      try {
        s3Client.headBucket(b -> b.bucket(bucketName));
        isDestinationPrepared = true;
        return null;
      } catch (NoSuchBucketException e) {
        throw new FileUploadFailedException("Target S3 artifact bucket " + bucketName + " doesn't exist", false);
      } catch (S3Exception e) {
        throw new FileUploadFailedException("Target S3 artifact bucket " + bucketName + " is not accessable", false, e);
      }
    });
  }
}
