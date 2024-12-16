package jetbrains.buildServer.artifacts.s3.download.parallel.splitter;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SplitabilityReport {
  private final boolean isSplittable;
  @Nullable
  private final String unsplitablilityReason;

  private SplitabilityReport(boolean isSplittable, @Nullable String unsplitablilityReason) {
    this.isSplittable = isSplittable;
    this.unsplitablilityReason = unsplitablilityReason;
  }

  public boolean isSplittable() {
    return isSplittable;
  }

  @Nullable
  public String getUnsplitablilityReason() {
    return unsplitablilityReason;
  }

  public static SplitabilityReport splittable() {
    return new SplitabilityReport(true, null);
  }

  public static SplitabilityReport unsplittable(@NotNull String reason) {
    return new SplitabilityReport(false, reason);
  }
}
