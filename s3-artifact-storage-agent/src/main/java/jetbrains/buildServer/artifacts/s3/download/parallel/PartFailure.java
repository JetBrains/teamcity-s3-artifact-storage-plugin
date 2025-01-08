package jetbrains.buildServer.artifacts.s3.download.parallel;


import java.io.IOException;
import org.jetbrains.annotations.NotNull;

public class PartFailure {
  @NotNull
  private final FilePart myPart;
  @NotNull
  private final IOException myException;

  public PartFailure(@NotNull FilePart part, @NotNull IOException exception) {
    myPart = part;
    myException = exception;
  }

  @NotNull
  public FilePart getPart() {
    return myPart;
  }

  @NotNull
  public IOException getException() {
    return myException;
  }
}
