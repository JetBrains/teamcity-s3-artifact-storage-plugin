package jetbrains.buildServer.artifacts.s3.publish.presigned.upload;

import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.DigestUtil;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.FilePart;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.LowLevelS3Client;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.S3MultipartUploadFileSplitter;
import jetbrains.buildServer.artifacts.s3.transport.PresignedUrlDto;
import jetbrains.buildServer.artifacts.s3.transport.PresignedUrlPartDto;
import jetbrains.buildServer.util.ExceptionUtil;
import jetbrains.buildServer.util.amazon.S3Util;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class S3PresignedMultipartUpload extends S3PresignedUpload {
  @NotNull
  private static final Logger LOGGER = Logger.getInstance(S3PresignedMultipartUpload.class);
  private final long myChunkSizeInBytes;
  private final S3MultipartUploadFileSplitter myFileSplitter;
  private final boolean myCheckConsistency;
  @Nullable
  private String[] myEtags;

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
    myEtags = null;
  }

  @Override
  protected String upload() throws IOException {
    LOGGER.debug(() -> "Multipart upload " + this + " started");
    final long totalLength = myFile.length();
    final int nParts = (int)(totalLength % myChunkSizeInBytes == 0 ? totalLength / myChunkSizeInBytes : totalLength / myChunkSizeInBytes + 1);
    if (myEtags == null) {
      myEtags = new String[nParts];
    }

    myProgressListener.beforeUploadStarted();
    try {
      final List<FilePart> fileParts = myFileSplitter.getFileParts(myFile, nParts, myCheckConsistency);
      List<String> digests = fileParts.stream().map(FilePart::getDigest).collect(Collectors.toList());
      final PresignedUrlDto multipartUploadUrls = myS3SignedUploadManager.getMultipartUploadUrls(myObjectKey, digests, uploadId, myTtl.get());

      if (uploadId == null) {
        uploadId = multipartUploadUrls.getUploadId();
      }

      for (PresignedUrlPartDto partDto : multipartUploadUrls.getPresignedUrlParts()) {
        try {

          final int partIndex = partDto.getPartNumber() - 1;
          if (myEtags[partIndex] == null) {
            myProgressListener.beforePartUploadStarted(partDto.getPartNumber());
            final String url = partDto.getUrl();
            final FilePart filePart = fileParts.get(partIndex);
            final String etag = myRetrier.execute(() -> myLowLevelS3Client.uploadFilePart(url, filePart));
            myRemainingBytes.getAndAdd(-filePart.getLength());
            myEtags[partIndex] = etag;
            myProgressListener.onPartUploadSuccess(stripQuery(url));
          }
        } catch (Exception e) {
          myProgressListener.onPartUploadFailed(e);
          ExceptionUtil.rethrowAsRuntimeException(e);
        }
      }
      final Iterator<PresignedUrlPartDto> iterator = multipartUploadUrls.getPresignedUrlParts().iterator();
      String strippedUrl = iterator.hasNext() ? stripQuery(iterator.next().getUrl()) : "";
      myProgressListener.onFileUploadSuccess(strippedUrl);
      return DigestUtil.multipartDigest(getEtags());
    } catch (IOException e) {
      LOGGER.warnAndDebugDetails("Multipart upload for " + this + " failed", e);
      myProgressListener.onFileUploadFailed(e, isRecoverable(e));
      throw new RuntimeException(e);
    } catch (final Exception e) {
      LOGGER.warnAndDebugDetails("Multipart upload for " + this + " failed", e);
      myProgressListener.onFileUploadFailed(e, isRecoverable(e));
      throw e;
    }
  }

  @NotNull
  public List<String> getEtags() {
    return myEtags != null ? Arrays.asList(myEtags) : Collections.emptyList();
  }
}
