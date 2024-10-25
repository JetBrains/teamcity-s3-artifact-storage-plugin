package jetbrains.buildServer.artifacts.s3.download;

import java.io.IOException;

public interface IORunnable {
  void run() throws IOException;
}
