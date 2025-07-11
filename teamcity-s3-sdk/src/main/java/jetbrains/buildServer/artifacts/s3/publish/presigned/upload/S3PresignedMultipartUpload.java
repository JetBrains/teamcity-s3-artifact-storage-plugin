package jetbrains.buildServer.artifacts.s3.publish.presigned.upload;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Function;
import java.util.stream.Collectors;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.DigestUtil;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.FilePart;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.LowLevelS3Client;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.S3MultipartUploadFileSplitter;
import jetbrains.buildServer.artifacts.s3.transport.PresignedUrlDto;
import jetbrains.buildServer.artifacts.s3.transport.PresignedUrlPartDto;
import jetbrains.buildServer.util.ExceptionUtil;
import jetbrains.buildServer.util.amazon.S3Util;
import jetbrains.buildServer.util.retry.AbortRetriesException;
import jetbrains.buildServer.util.retry.RecoverableException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.artifacts.s3.S3Util.getContentType;

public class S3PresignedMultipartUpload extends S3PresignedUpload {
  @NotNull
  private static final Logger LOGGER = Logger.getInstance(S3PresignedMultipartUpload.class);
  private final long myChunkSizeInBytes;
  private final S3MultipartUploadFileSplitter myFileSplitter;
  private final boolean myCheckConsistency;
  @Nullable
  private AtomicReferenceArray<String> myEtags;

  @Nullable
  private String uploadId;
  @Nullable
  private List<FilePart> myFileParts;

  public S3PresignedMultipartUpload(@NotNull String artifactPath,
                                    @NotNull String objectKey,
                                    @NotNull File file,
                                    @NotNull S3Util.S3AdvancedConfiguration configuration,
                                    @NotNull S3SignedUploadManager s3SignedUploadManager,
                                    @NotNull LowLevelS3Client lowLevelS3Client,
                                    @NotNull PresignedUploadProgressListener progressListener) {
    super(artifactPath, objectKey, file, configuration, s3SignedUploadManager, lowLevelS3Client, progressListener);
    myEtags = null;
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
      splitFileToPartsWithChecksum(nParts);
      assert myFileParts != null;
      List<String> digests = myFileParts.stream()
        .map(FilePart::getDigest)
        .collect(Collectors.toList());
      String contentType = getContentType(myFile);
      final Pair<PresignedUrlDto, Long> resultPair = myS3SignedUploadManager.getMultipartUploadUrls(
        myObjectKey,
        contentType,
        digests,
        uploadId,
        myTtl.get()
      );
      final PresignedUrlDto multipartUploadUrls = resultPair.first;
      myProgressListener.urlsGenerated(Duration.ofNanos(resultPair.second));

      if (uploadId == null) {
        uploadId = multipartUploadUrls.getUploadId();
      }

      // initialize array of results
      // it will be reused by upper-level retry procedure, that in some cases will be triggered
      // (see jetbrains.buildServer.artifacts.s3.publish.presigned.upload.S3SignedUrlFileUploader::upload)
      if (myEtags == null) {
        myEtags = new AtomicReferenceArray<>(nParts);
      }

      // actually we don't need results from these futures.
      // each completion will directly write result to the array of results called myEtags.
      CompletableFuture[] chunkUploadFutures = multipartUploadUrls.getPresignedUrlParts()
                                                                  .stream()
                                                                  .map(partDto -> {
                                                                    final int partIndex = partDto.getPartNumber() - 1;
                                                                    final FilePart filePart = myFileParts.get(partIndex);
                                                                    myProgressListener.beforePartUploadStarted(partIndex, filePart.getLength());
                                                                    final String url = partDto.getUrl();

                                                                    // this allows us to save time in case of huge files re-upload
                                                                    if (myEtags.get(partIndex) != null) {
                                                                      // part was already uploaded
                                                                      return CompletableFuture.completedFuture(myEtags.get(partIndex));
                                                                    }

                                                                    return myRetrier.executeAsync(() -> {
                                                                                      try {
                                                                                        return myLowLevelS3Client.uploadFilePart(url, filePart);
                                                                                      } catch (URISyntaxException e) {
                                                                                        throw new AbortRetriesException(e);
                                                                                      }
                                                                                    })
                                                                                    .thenApply(onUploadSuccess(partDto, partIndex, url, filePart))
                                                                                    .exceptionally(e -> {
                                                                                      myProgressListener.onPartUploadFailed(e, partIndex);
                                                                                      ExceptionUtil.rethrowAsRuntimeException(e);
                                                                                      return null;
                                                                                    });
                                                                  })
                                                                  .toArray(CompletableFuture[]::new);
      // await completion of all futures.
      // in case of exception in any of them, we will cancel all the futures immediately
      allOfTerminateOnFailure(chunkUploadFutures).get();
      final Iterator<PresignedUrlPartDto> iterator = multipartUploadUrls.getPresignedUrlParts().iterator();
      String digest = DigestUtil.multipartDigest(getEtags());
      myProgressListener.onFileUploadSuccess(digest);
      return digest;
    } catch (final Exception e) {
      Exception cause = stripRootCause(e);
      boolean isRecoverable = canRetry(cause);
      LOGGER.warnAndDebugDetails("Multipart upload for " + this + " failed", cause);

      if (!isRecoverable) {
        resetUploadId();
      }

      myProgressListener.onFileUploadFailed(cause.toString(), isRecoverable);
      // InterruptedException will be re-thrown wrapped in RuntimeException
      ExceptionUtil.rethrowAsRuntimeException(cause);
      return null;
    }
  }

  @NotNull
  private Function<String, String> onUploadSuccess(PresignedUrlPartDto partDto, int partIndex, String url, FilePart filePart) {
    return etag -> {
      myRemainingBytes.getAndAdd(-filePart.getLength());
      myUploadedBytes.getAndAdd(filePart.getLength());
      LOGGER.debug(
        () -> new StringBuilder().append("Part ")
                                 .append(partDto.getPartNumber())
                                 .append(" uploaded successfully. ")
                                 .append(myUploadedBytes.get())
                                 .append(" bytes uploaded, ")
                                 .append(myRemainingBytes.get())
                                 .append(" bytes remaining")
                                 .toString());
      myProgressListener.onPartUploadSuccess(stripQuery(url), partIndex, filePart.getDigest());
      // put result to the array of results
      assert myEtags != null;
      myEtags.set(partIndex, etag);
      return etag;
    };
  }

  private void splitFileToPartsWithChecksum(int nParts) throws IOException {
    // do the digest calculation only once
    if (myFileParts == null) {
      final long partSeparationStart = System.nanoTime();
      myFileParts = myFileSplitter.getFileParts(myFile, nParts, myCheckConsistency);
      final long partSeparationEnd = System.nanoTime();
      myProgressListener.partsSeparated(nParts, myChunkSizeInBytes, Duration.ofNanos(partSeparationEnd - partSeparationStart));
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

  private void resetUploadId() {
    LOGGER.debug("Multipart upload with id " + uploadId + " has it's uploadId reset to null. Due to non-recoverable error.");
    uploadId = null;
    myEtags = null;
  }

  private boolean canRetry(Throwable cause) {
    return Arrays.stream(arrayOfRetriableErrors).anyMatch(it -> it.isInstance(cause)) ||
           cause instanceof RecoverableException;
  }

  @NotNull
  public List<String> getEtags() {
    if (myEtags == null) {
      return Collections.emptyList();
    }

    ArrayList<String> result = new ArrayList<>(myEtags.length());

    for (int i = 0; i < myEtags.length(); i++) {
      String etag = myEtags.get(i);
      if (etag != null) {
        result.add(etag);
      } else {
        // exit immediately if at least one etag is not ready
        return Collections.emptyList();
      }
    }

    return result;
  }
}
