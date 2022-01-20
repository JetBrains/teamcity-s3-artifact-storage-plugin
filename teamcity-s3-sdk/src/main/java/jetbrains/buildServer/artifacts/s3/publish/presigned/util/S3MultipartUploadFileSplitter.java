package jetbrains.buildServer.artifacts.s3.publish.presigned.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class S3MultipartUploadFileSplitter {

  private final long myChunkSizeInBytes;

  public S3MultipartUploadFileSplitter(long chunkSizeInBytes) {
    myChunkSizeInBytes = chunkSizeInBytes;
  }

  @NotNull
  public List<FilePart> getFileParts(@NotNull File file, int nParts, boolean checkConsistency) throws IOException {
    final List<FilePart> results = new ArrayList<>();
    for (int partIndex = 0; partIndex < nParts; partIndex++) {
      final long start = partIndex * myChunkSizeInBytes;
      final long contentLength = Math.min(myChunkSizeInBytes, file.length() - myChunkSizeInBytes * partIndex);
      final FilePart part = new FilePart(file, start, contentLength, partIndex + 1);
      if (checkConsistency) {
        part.calculateDigest();
      }
      results.add(part);
    }
    return results;
  }
}
