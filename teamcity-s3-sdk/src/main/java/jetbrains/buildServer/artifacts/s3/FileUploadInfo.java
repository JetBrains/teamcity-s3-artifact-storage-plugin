package jetbrains.buildServer.artifacts.s3;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FileUploadInfo {
  @NotNull
  private final String myAbsolutePath;
  private final long mySize;
  @Nullable
  private final String myDigest;
  @NotNull
  private final String myArtifactPath;

  public FileUploadInfo(@NotNull final String artifactPath, @NotNull String absolutePath, final long size, @Nullable String digest) {
    myArtifactPath = artifactPath;
    myAbsolutePath = absolutePath;
    mySize = size;
    myDigest = digest;
  }

  public long getSize() {
    return mySize;
  }

  @NotNull
  public String getArtifactPath() {
    return myArtifactPath;
  }

  @NotNull
  public String getAbsolutePath() {
    return myAbsolutePath;
  }

  @Nullable
  public String getDigest() {
    return myDigest;
  }
}
