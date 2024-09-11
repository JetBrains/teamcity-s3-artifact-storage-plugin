package jetbrains.buildServer.artifacts.s3.util;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum RegionSortPriority {
  US("us", 10),
  EU("eu", 20),
  CA("ca", 30),
  AP("ap", 40),
  SA("sa", 50),
  DEFAULT("default", 55),
  CN("cn", 60),
  US_ISO("us-iso", 70),
  US_ISOB("us-isob", 80),
  US_GOV("us-gov", 90);

  private static final Map<String, RegionSortPriority> PREFIX_TO_REGION_SORT_PRIORITY =
    Arrays.stream(RegionSortPriority.values())
      .collect(Collectors.toMap(k -> k.myPrefix, Function.identity()));

  private final String myPrefix;
  private final int myPriority;

  RegionSortPriority(@NotNull final String prefix, final int priority) {
    myPrefix = prefix;
    myPriority = priority;
  }

  public String getPrefix() {
    return myPrefix;
  }

  public int getPriority() {
    return myPriority;
  }

  public static RegionSortPriority getFromPrefix(@NotNull final String prefix) {
    return PREFIX_TO_REGION_SORT_PRIORITY.getOrDefault(prefix, RegionSortPriority.DEFAULT);
  }
}
