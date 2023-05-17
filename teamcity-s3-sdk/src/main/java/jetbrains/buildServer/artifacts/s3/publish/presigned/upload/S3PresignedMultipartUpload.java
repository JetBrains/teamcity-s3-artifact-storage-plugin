package jetbrains.buildServer.artifacts.s3.publish.presigned.upload;

import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.DigestUtil;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.FilePart;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.LowLevelS3Client;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.S3MultipartUploadFileSplitter;
import jetbrains.buildServer.artifacts.s3.transport.PresignedUrlDto;
import jetbrains.buildServer.artifacts.s3.transport.PresignedUrlPartDto;
import jetbrains.buildServer.util.ExceptionUtil;
import jetbrains.buildServer.util.amazon.S3Util;
import jetbrains.buildServer.util.amazon.retry.AbortRetriesException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class S3PresignedMultipartUpload extends S3PresignedUpload {
  @NotNull
  private static final Logger LOGGER = Logger.getInstance(S3PresignedMultipartUpload.class);
  private final long myChunkSizeInBytes;
  private final S3MultipartUploadFileSplitter myFileSplitter;
  private final boolean myCheckConsistency;
  @Nullable
  private CompletableFuture<String>[] myEtags;

  @Nullable
  private String uploadId;

  public S3PresignedMultipartUpload(@NotNull String artifactPath,
                                    @NotNull String objectKey,
                                    @NotNull File file,
                                    @NotNull S3Util.S3AdvancedConfiguration configuration,
                                    @NotNull S3SignedUploadManager s3SignedUploadManager,
                                    @NotNull LowLevelS3Client lowLevelS3Client,
                                    @NotNull PresignedUploadProgressListener progressListener) {
    super(artifactPath, objectKey, file, configuration, s3SignedUploadManager, lowLevelS3Client, progressListener);

    myCheckConsistency = configuration.isConsistencyCheckEnabled();
    myChunkSizeInBytes = configuration.getMinimumUploadPartSize();
    myFileSplitter = new S3MultipartUploadFileSplitter(myChunkSizeInBytes);
  }

  @Override
  protected String upload() throws IOException {
    LOGGER.debug(() -> "Multipart upload " + this + " started");
    final long totalLength = myFile.length();
    final int nParts = (int)(totalLength % myChunkSizeInBytes == 0 ? totalLength / myChunkSizeInBytes : totalLength / myChunkSizeInBytes + 1);

    myProgressListener.beforeUploadStarted();
    try {
      final long partSeparationStart = System.nanoTime();
      final List<FilePart> fileParts = myFileSplitter.getFileParts(myFile, nParts, myCheckConsistency);
      final long partSeparationEnd = System.nanoTime();
      myProgressListener.partsSeparated(Duration.ofNanos(partSeparationEnd - partSeparationStart));
      List<String> digests = fileParts.stream().map(FilePart::getDigest).collect(Collectors.toList());

      final long urlRequestStart = System.nanoTime();
      final PresignedUrlDto multipartUploadUrls = myS3SignedUploadManager.getMultipartUploadUrls(myObjectKey, digests, uploadId, myTtl.get());
      final long urlRequestEnd = System.nanoTime();
      myProgressListener.urlsGenerated(Duration.ofNanos(urlRequestEnd - urlRequestStart));

      if (uploadId == null) {
        uploadId = multipartUploadUrls.getUploadId();
      }

      //noinspection unchecked
      myEtags = multipartUploadUrls.getPresignedUrlParts()
                                   .stream()
                                   .map(partDto -> {
                                     final int partIndex = partDto.getPartNumber() - 1;
                                     myProgressListener.beforePartUploadStarted(partDto.getPartNumber());
                                     final String url = partDto.getUrl();
                                     final FilePart filePart = fileParts.get(partIndex);
                                     return myRetrier.executeAsync(() -> {
                                                       try {
                                                         return myLowLevelS3Client.uploadFilePart(url, filePart);
                                                       } catch (URISyntaxException e) {
                                                         throw new AbortRetriesException(e);
                                                       }
                                                     })
                                                     .thenApply(etag -> {
                                                       myRemainingBytes.getAndAdd(-filePart.getLength());
                                                       myProgressListener.onPartUploadSuccess(stripQuery(url));
                                                       return etag;
                                                     })
                                                     .exceptionally(e -> {
                                                       myProgressListener.onPartUploadFailed(e);
                                                       ExceptionUtil.rethrowAsRuntimeException(e);
                                                       return null;
                                                     });
                                   })
                                   .toArray(CompletableFuture[]::new);

      allOfTerminateOnFailure(myEtags).get();
      final Iterator<PresignedUrlPartDto> iterator = multipartUploadUrls.getPresignedUrlParts().iterator();
      String strippedUrl = iterator.hasNext() ? stripQuery(iterator.next().getUrl()) : "";
      myProgressListener.onFileUploadSuccess(strippedUrl);
      return DigestUtil.multipartDigest(getEtags());
    } catch (InterruptedException | ExecutionException e) {
      LOGGER.warnAndDebugDetails("Multipart upload for " + this + " failed", e);

      // ExecutionException wraps the real reason for termination or failure.
      // see this::allOfTerminateOnFailure for details
      if (e instanceof ExecutionException && e.getCause() != null) {
        Throwable cause = e.getCause();
        myProgressListener.onFileUploadFailed(cause.getMessage(), isRecoverable((Exception)cause));
        ExceptionUtil.rethrowAsRuntimeException(cause);
      } else {
        myProgressListener.onFileUploadFailed(e.getMessage(), isRecoverable(e));
        ExceptionUtil.rethrowAsRuntimeException(e);
      }

      return null;
    } catch (final Exception e) {
      LOGGER.warnAndDebugDetails("Multipart upload for " + this + " failed", e);
      myProgressListener.onFileUploadFailed(e.getMessage(), isRecoverable(e));
      throw e;
    }
  }

  private CompletableFuture<?> allOfTerminateOnFailure(CompletableFuture<String>... futures) {
    CompletableFuture<?> failure = new CompletableFuture<>();
    for (CompletableFuture<String> future : futures) {
      future.exceptionally(ex -> {
        failure.completeExceptionally(ex);
        cancelAll(futures);
        return null;
      });
    }

    return CompletableFuture.anyOf(failure, CompletableFuture.allOf(futures));
  }

  private static void cancelAll(CompletableFuture<String>[] futures) {
    for (CompletableFuture<String> future : futures) {
      future.cancel(true);
    }
  }

  @NotNull
  public List<String> getEtags() {
    if (myEtags == null) {
      return Collections.emptyList();
    }
    final CompletableFuture<Void> etagsFuture = CompletableFuture.allOf(myEtags);

    if (etagsFuture.isDone()) {
      return etagsFuture.isCompletedExceptionally() || etagsFuture.isCancelled() ? Collections.emptyList() :
             Arrays.stream(myEtags).map(CompletableFuture::join).collect(Collectors.toList());
    } else {
      return Collections.emptyList();
    }
  }
}
