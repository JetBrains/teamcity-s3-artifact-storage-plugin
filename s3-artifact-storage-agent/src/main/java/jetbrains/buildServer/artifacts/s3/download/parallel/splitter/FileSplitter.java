package jetbrains.buildServer.artifacts.s3.download.parallel.splitter;

import java.util.List;
import jetbrains.buildServer.artifacts.s3.download.parallel.FilePart;
import org.jetbrains.annotations.NotNull;

public interface FileSplitter {

  @NotNull
  List<FilePart> split(long fileSize);

  @NotNull
  SplitabilityReport testSplitability(long fileSize);
}
