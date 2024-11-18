package jetbrains.buildServer.artifacts.s3.download;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.IntConsumer;
import jetbrains.buildServer.artifacts.RecoverableIOException;
import org.jetbrains.annotations.NotNull;

public final class S3DownloadFileUtil {
  @NotNull
  public static Path getUnfinishedFilePath(@NotNull Path file) {
    String fileName = file.getFileName().toString();
    return file.getParent().resolve(fileName + ".unfinished");
  }

  public static void ensureDirectoryExists(@NotNull Path directory) throws IOException {
    try {
      if (Files.isDirectory(directory)) return;
      Files.createDirectories(directory);
    } catch (IOException e) {
      throw new IOException(String.format("Failed to create directory hierarchy %s", directory), e);
    }
  }

  public static void reserveBytes(@NotNull Path file, long bytes) throws IOException {
    if (bytes < 0) throw new IllegalArgumentException(String.format("Number of bytes is negative (%s)", bytes));

    try (SeekableByteChannel fileChannel = Files.newByteChannel(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
      fileChannel.position(bytes - 1);
      fileChannel.write(ByteBuffer.wrap(new byte[]{0}));
    }
  }

  public static void transferExpectedBytesToPositionedTarget(@NotNull ReadableByteChannel sourceChannel,
                                                             @NotNull SeekableByteChannel targetChannel,
                                                             long targetPosition,
                                                             long expectedBytes,
                                                             int bufferSize,
                                                             @NotNull IORunnable interruptedCheck,
                                                             @NotNull IntConsumer progressTracker
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
                                           @NotNull IntConsumer progressTracker
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
