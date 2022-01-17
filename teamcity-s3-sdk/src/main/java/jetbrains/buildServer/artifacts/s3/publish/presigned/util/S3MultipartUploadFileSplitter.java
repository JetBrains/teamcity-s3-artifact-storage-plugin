package jetbrains.buildServer.artifacts.s3.publish.presigned.util;

import com.intellij.openapi.util.Pair;
import java.io.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.apache.commons.codec.digest.DigestUtils;
import org.jetbrains.annotations.NotNull;

public class S3MultipartUploadFileSplitter {
  private static final int OUR_CHUNK_SIZE = TeamCityProperties.getInteger(S3Constants.S3_PRESIGNED_UPLOAD_INTERNAL_CHUNK_SIZE, 64 * 1024);

  private final long myChunkSizeInBytes;

  public S3MultipartUploadFileSplitter(long chunkSizeInBytes) {
    myChunkSizeInBytes = chunkSizeInBytes;
  }

  @NotNull
  public Pair<List<byte[]>, List<String>> getFileParts(@NotNull File file, int nParts) throws IOException {
    final List<byte[]> parts = new ArrayList<>();
    final List<String> digests = new ArrayList<>();
    for (int partIndex = 0; partIndex < nParts; partIndex++) {
      final long contentLength = Math.min(myChunkSizeInBytes, file.length() - myChunkSizeInBytes * partIndex);
      final long start = partIndex * myChunkSizeInBytes;
      final byte[] bytes = getFilePart(file, start, contentLength);
      final String encodedDigest = Base64.getEncoder().encodeToString(DigestUtils.md5(bytes));
      parts.add(bytes);
      digests.add(encodedDigest);
    }
    return Pair.create(parts, digests);
  }

  private byte[] getFilePart(@NotNull File file, long start, long length) throws IOException {
    long remaining = length;
    final byte[] buffer = new byte[(int)Math.min(OUR_CHUNK_SIZE, remaining)];

    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (final FileInputStream fis = new FileInputStream(file);
         final BufferedInputStream bis = new BufferedInputStream(fis)) {

      final long skip = bis.skip(start);
      if (skip != start) {
        throw new IOException("Reader has skipped " + skip + " bytes when supposed to skip " + start);
      }
      do {
        final int currentChunkSize = (int)Math.min(buffer.length, remaining);
        final int readContentLength = bis.read(buffer, 0, currentChunkSize);
        if (readContentLength == -1) {
          //eof
          break;
        }
        remaining -= readContentLength;
        out.write(buffer, 0, readContentLength);
      } while (remaining > 0);
    }
    return out.toByteArray();
  }

}
