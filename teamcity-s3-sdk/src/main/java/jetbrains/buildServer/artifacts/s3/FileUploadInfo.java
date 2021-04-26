package jetbrains.buildServer.artifacts.s3;

import org.jetbrains.annotations.NotNull;

public class FileUploadInfo {
  private final long mySize;
  @NotNull
  private final String myArtifactPath;

  public FileUploadInfo(@NotNull final String artifactPath, final long size) {
    myArtifactPath = artifactPath;
    mySize = size;
  }

  public long getSize() {
    return mySize;
  }

  @NotNull
  public String getArtifactPath() {
    return myArtifactPath;
  }
}
