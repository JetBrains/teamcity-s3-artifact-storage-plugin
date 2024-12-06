package jetbrains.buildServer.artifacts.s3.download.parallel;


import java.io.IOException;
import org.jetbrains.annotations.NotNull;

public class PartFailure {
  @NotNull
  private final FilePart part;
  @NotNull
  private final IOException exception;

  public PartFailure(@NotNull FilePart part, @NotNull IOException exception) {
    this.part = part;
    this.exception = exception;
  }

  @NotNull
  public FilePart getPart() {
    return part;
  }

  @NotNull
  public IOException getException() {
    return exception;
  }
}
