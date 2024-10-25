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
    try (SeekableByteChannel fileChannel = Files.newByteChannel(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
      fileChannel.position(bytes - 1);
      fileChannel.write(ByteBuffer.wrap(new byte[]{0}));
    }
  }

  public static void transferBytesToPositionedTarget(@NotNull ReadableByteChannel sourceChannel,
                                                     @NotNull SeekableByteChannel targetChannel,
                                                     long targetPosition,
                                                     long expectedBytes,
                                                     int bufferSize,
                                                     @NotNull IORunnable interruptedCheck,
                                                     @NotNull IntConsumer progressTracker
  ) throws IOException {
    interruptedCheck.run();
    targetChannel.position(targetPosition);
    transferBytes(sourceChannel, targetChannel, expectedBytes, bufferSize, interruptedCheck, progressTracker);
  }

  public static void transferBytes(@NotNull ReadableByteChannel sourceChannel,
                                   @NotNull WritableByteChannel targetChannel,
                                   long expectedBytes,
                                   int bufferSize,
                                   @NotNull IORunnable interruptedCheck,
                                   @NotNull IntConsumer progressTracker
  ) throws IOException {
    long transferred = 0L;
    ByteBuffer byteBuffer = ByteBuffer.allocate((int)Math.min(expectedBytes, bufferSize));
    while (sourceChannel.read(byteBuffer) > 0) {
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
}
