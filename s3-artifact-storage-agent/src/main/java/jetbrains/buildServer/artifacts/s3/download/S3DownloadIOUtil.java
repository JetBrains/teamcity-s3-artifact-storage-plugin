package jetbrains.buildServer.artifacts.s3.download;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Objects;
import java.util.function.LongConsumer;
import jetbrains.buildServer.artifacts.RecoverableIOException;
import org.jetbrains.annotations.NotNull;

import static java.nio.file.StandardOpenOption.*;
import static java.nio.file.StandardOpenOption.SPARSE;

public final class S3DownloadIOUtil {
  @NotNull
  public static Path getAbsoluteNormalizedPath(@NotNull Path path) {
    return path.toAbsolutePath().normalize();
  }

  @NotNull
  public static Path getUnfinishedFilePath(@NotNull Path file) {
    Path absoluteNormalizedFilePath = getAbsoluteNormalizedPath(file);
    Path fileNamePath = absoluteNormalizedFilePath.getFileName();
    Objects.requireNonNull(fileNamePath, String.format("File name must not be null, file=%s", file));
    Path parentDirectoryPath = absoluteNormalizedFilePath.getParent();
    Objects.requireNonNull(parentDirectoryPath, String.format("Parent directory must not be null, file=%s", file));
    return parentDirectoryPath.resolve(fileNamePath + ".unfinished");
  }

  @NotNull
  public static Path getFilePartPath(@NotNull Path file, int partNumber, @NotNull Path tempPartsDirectory) {
    Path absoluteNormalizedFilePath = getAbsoluteNormalizedPath(file);
    Path fileNamePath = absoluteNormalizedFilePath.getFileName();
    Objects.requireNonNull(fileNamePath, String.format("File name must not be null, file=%s", file));
    return tempPartsDirectory.resolve(fileNamePath + ".part." + partNumber);
  }

  public static void ensureDirectoryExists(@NotNull Path directory) throws IOException {
    if (Files.isDirectory(directory)) return;
    Files.createDirectories(directory);
  }

  public static void reserveFileBytes(@NotNull Path file, long bytes) throws IOException {
    if (bytes <= 0) throw new IllegalArgumentException(String.format("Number of bytes is not positive: %s", bytes));

    try (SeekableByteChannel fileChannel = Files.newByteChannel(file, EnumSet.of(CREATE, WRITE))) {
      fileChannel.position(bytes - 1);
      fileChannel.write(ByteBuffer.wrap(new byte[]{0}));
      fileChannel.truncate(bytes); // needed if the file already exists and is larger than bytes
    }
  }

  public static void createFile(@NotNull Path file, boolean isSparse) throws IOException {
    if (Files.isDirectory(file)) throw new IOException(String.format("%s is a directory", file));

    Files.deleteIfExists(file);
    if (isSparse) {
      // to create a sparse file, we have to use a channel
      // we just need to open the channel with specific options and can close it right away
      Files.newByteChannel(file, CREATE_NEW, WRITE, SPARSE).close();
    } else {
      Files.createFile(file);
    }
  }

  // general channel transfer

  public static void transferExpectedBytes(@NotNull ReadableByteChannel sourceChannel,
                                           @NotNull WritableByteChannel targetChannel,
                                           long expectedBytes,
                                           int bufferSize,
                                           @NotNull IORunnable interruptedCheck,
                                           @NotNull LongConsumer progressTracker
  ) throws IOException {
    transferBytes(sourceChannel, targetChannel, true, expectedBytes, bufferSize, interruptedCheck, progressTracker);
  }

  public static void transferAllBytes(@NotNull ReadableByteChannel sourceChannel,
                                      @NotNull WritableByteChannel targetChannel,
                                      int bufferSize,
                                      @NotNull IORunnable interruptedCheck,
                                      @NotNull LongConsumer progressTracker
  ) throws IOException {
    transferBytes(sourceChannel, targetChannel, false, -1, bufferSize, interruptedCheck, progressTracker);
  }

  private static void transferBytes(@NotNull ReadableByteChannel sourceChannel,
                            @NotNull WritableByteChannel targetChannel,
                            boolean expectedCheck,
                            long expectedBytes,
                            int bufferSize,
                            @NotNull IORunnable interruptedCheck,
                            @NotNull LongConsumer progressTracker
  ) throws IOException {
    interruptedCheck.run();
    if (expectedCheck && expectedBytes < 0) throw new IllegalArgumentException(String.format("Expecting negative number of bytes (%s)", expectedBytes));
    if (bufferSize <= 0) throw new IllegalArgumentException(String.format("Buffer size is not positive (%s)", bufferSize));

    ByteBuffer byteBuffer = ByteBuffer.allocate(expectedCheck && expectedBytes > 0 ? (int)Math.min(expectedBytes, bufferSize) : bufferSize);
    long transferred = 0;
    while (sourceChannel.read(byteBuffer) >= 0) {
      byteBuffer.flip();
      while (byteBuffer.hasRemaining()) {
        long toBeTransferred = byteBuffer.remaining() + transferred;
        if (expectedCheck && toBeTransferred > expectedBytes) {
          throw new RecoverableIOException(String.format("Received more bytes from source channel (at least %s) than expected (%s)", toBeTransferred, expectedBytes));
        }

        interruptedCheck.run();
        int written = targetChannel.write(byteBuffer);
        transferred += written;
        progressTracker.accept(written);
      }
      byteBuffer.clear();
    }

    if (expectedCheck && transferred < expectedBytes) {
      throw new RecoverableIOException(String.format("Received less bytes from source channel (%s) than expected (%s)", transferred, expectedBytes));
    }
  }

  // optimized file channel transfer that can bypass heap

  public static void transferExpectedFileBytes(@NotNull FileChannel sourceFileChannel,
                                               @NotNull FileChannel targetFileChannel,
                                               long expectedBytes,
                                               @NotNull IORunnable interruptedCheck,
                                               @NotNull LongConsumer progressTracker
  ) throws IOException {
    interruptedCheck.run();
    if (expectedBytes < 0) throw new IllegalArgumentException(String.format("Expecting negative number of bytes (%s)", expectedBytes));

    long sourceFileSize = sourceFileChannel.size();
    long sourceChannelPosition = sourceFileChannel.position();
    if (expectedBytes != sourceFileSize - sourceChannelPosition) {
      throw new IOException(
        String.format("Source file (size: %s) has different number of bytes to transfer from position (%s) than expected (%s)", sourceFileSize, sourceChannelPosition, expectedBytes)
      );
    }

    long totalTransferred = 0;
    while (totalTransferred < expectedBytes) {
      interruptedCheck.run();
      long transferred = sourceFileChannel.transferTo(sourceChannelPosition + totalTransferred, expectedBytes - totalTransferred, targetFileChannel);
      totalTransferred += transferred;
      progressTracker.accept(transferred);
    }
  }
}
