package jetbrains.buildServer.artifacts.s3.download;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import jetbrains.buildServer.artifacts.RecoverableIOException;
import org.jetbrains.annotations.NotNull;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

public final class S3DownloadIOUtil {
  @NotNull
  public static Path getAbsoluteNormalizedPath(@NotNull Path path) {
    return path.toAbsolutePath().normalize();
  }

  @NotNull
  public static Path getUnfinishedFilePath(@NotNull Path file) {
    Path absoluteNormalizedFilePath = getAbsoluteNormalizedPath(file);
    Path fileNamePath = absoluteNormalizedFilePath.getFileName();
    Path parentDirectoryPath = absoluteNormalizedFilePath.getParent();
    Objects.requireNonNull(fileNamePath, String.format("File name must not be null, file=%s", file));
    Objects.requireNonNull(parentDirectoryPath, String.format("Parent directory must not be null, file=%s", file));
    return parentDirectoryPath.resolve(fileNamePath + ".unfinished");
  }

  public static void ensureDirectoryExists(@NotNull Path directory) throws IOException {
    if (Files.isDirectory(directory)) return;
    Files.createDirectories(directory);
  }

  public static void reserveFileBytes(@NotNull Path file, long bytes) throws IOException {
    if (bytes < 0) throw new IllegalArgumentException(String.format("Number of bytes is negative: %s", bytes));

    try (SeekableByteChannel fileChannel = Files.newByteChannel(file, EnumSet.of(CREATE, WRITE))) {
      fileChannel.position(bytes - 1);
      fileChannel.write(ByteBuffer.wrap(new byte[]{0}));
      fileChannel.truncate(bytes); // needed if the file already exists and is larger than bytes
    }
  }

  public static void transferExpectedBytesToPositionedTarget(@NotNull ReadableByteChannel sourceChannel,
                                                             @NotNull SeekableByteChannel targetChannel,
                                                             long targetPosition,
                                                             long expectedBytes,
                                                             int bufferSize,
                                                             @NotNull IORunnable interruptedCheck,
                                                             @NotNull LongConsumer progressTracker
  ) throws IOException {
    if (targetPosition < 0) throw new IllegalArgumentException(String.format("Target position is negative (%s)", targetPosition));

    interruptedCheck.run();
    targetChannel.position(targetPosition);
    transferExpectedBytes(sourceChannel, targetChannel, expectedBytes, bufferSize, interruptedCheck, progressTracker);
  }

  public static void transferExpectedBytes(@NotNull ReadableByteChannel sourceChannel,
                                           @NotNull WritableByteChannel targetChannel,
                                           long expectedBytes,
                                           int bufferSize,
                                           @NotNull IORunnable interruptedCheck,
                                           @NotNull LongConsumer progressTracker
  ) throws IOException {
    long transferred = 0L;
    if (expectedBytes < 0) throw new IllegalArgumentException(String.format("Expecting negative number of bytes (%s)", expectedBytes));
    if (bufferSize <= 0) throw new IllegalArgumentException(String.format("Buffer size is not positive (%s)", bufferSize));

    ByteBuffer byteBuffer = ByteBuffer.allocate(expectedBytes > 0 ? (int)Math.min(expectedBytes, bufferSize) : bufferSize);
    while (sourceChannel.read(byteBuffer) >= 0) {
      byteBuffer.flip();
      while (byteBuffer.hasRemaining()) {
        long toBeTransferred = byteBuffer.remaining() + transferred;
        if (toBeTransferred > expectedBytes) throw new RecoverableIOException(String.format("More bytes (at least %s) than expected (%s)", toBeTransferred, expectedBytes));

        interruptedCheck.run();
        int written = targetChannel.write(byteBuffer);
        transferred += written;
        progressTracker.accept(written);
      }
      byteBuffer.clear();
    }

    if (transferred < expectedBytes) throw new RecoverableIOException(String.format("Less bytes (%s) than expected (%s)", transferred, expectedBytes));
  }

  public static void transferAllBytes(@NotNull ReadableByteChannel sourceChannel,
                                      @NotNull WritableByteChannel targetChannel,
                                      int bufferSize,
                                      @NotNull IORunnable interruptedCheck,
                                      @NotNull IntConsumer progressTracker
  ) throws IOException {
    if (bufferSize <= 0) throw new IllegalArgumentException(String.format("Buffer size is not positive (%s)", bufferSize));

    ByteBuffer byteBuffer = ByteBuffer.allocate(bufferSize);
    while (sourceChannel.read(byteBuffer) >= 0) {
      byteBuffer.flip();
      while (byteBuffer.hasRemaining()) {
        interruptedCheck.run();
        int written = targetChannel.write(byteBuffer);
        progressTracker.accept(written);
      }
      byteBuffer.clear();
    }
  }
}
