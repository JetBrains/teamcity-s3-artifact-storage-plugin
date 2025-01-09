package jetbrains.buildServer.artifacts.s3.download.parallel.splitter;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SplitabilityReport {
  private final boolean myIsSplittable;
  @Nullable
  private final String myUnsplitablilityReason;

  private SplitabilityReport(boolean isSplittable, @Nullable String unsplitablilityReason) {
    myIsSplittable = isSplittable;
    myUnsplitablilityReason = unsplitablilityReason;
  }

  public boolean isSplittable() {
    return myIsSplittable;
  }

  @Nullable
  public String getUnsplitablilityReason() {
    return myUnsplitablilityReason;
  }

  public static SplitabilityReport splittable() {
    return new SplitabilityReport(true, null);
  }

  public static SplitabilityReport unsplittable(@NotNull String reason) {
    return new SplitabilityReport(false, reason);
  }
}
