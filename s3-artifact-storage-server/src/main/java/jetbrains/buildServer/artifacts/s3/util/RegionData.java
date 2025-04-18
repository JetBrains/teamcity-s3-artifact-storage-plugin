package jetbrains.buildServer.artifacts.s3.util;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.regions.Regions;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * @deprecated use #{@link jetbrains.buildServer.clouds.amazon.connector.utils.parameters.regions.AWSRegions}
 */
@Deprecated
public final class RegionData {

  private static final String SERVICE_NAME = "s3";
  private static final String REGEX_SEPARATOR = "|";
  private static final String SPECIAL_DESIGNATION_REGIONS_PREFIX = Stream.of(
      RegionSortPriority.US_GOV,
      RegionSortPriority.US_ISO,
      RegionSortPriority.US_ISOB)
    .map(RegionSortPriority::getPrefix)
    .collect(Collectors.joining(REGEX_SEPARATOR));
  private static final Pattern SPECIAL_DESIGNATION_PATTERN = Pattern.compile("^(" + SPECIAL_DESIGNATION_REGIONS_PREFIX + ")-.*$");
  private static final char REGION_SEPARATOR = '-';
  private static final Comparator<String> REGION_COMPARATOR = Comparator.comparingInt(RegionData::getRegionalPriority)
    .thenComparing(Comparator.naturalOrder());

  private static int getRegionalPriority(String region) {
    String prefix;

    if (SPECIAL_DESIGNATION_PATTERN.matcher(region).matches()) {
      int firstSeparatorIndex = region.indexOf(REGION_SEPARATOR);
      prefix = region.substring(0, region.indexOf('-', firstSeparatorIndex + 1));
    } else {
      prefix = region.substring(0, region.indexOf(REGION_SEPARATOR));
    }

    return RegionSortPriority.getFromPrefix(prefix).getPriority();
  }

  @SuppressWarnings("FieldCanBeLocal")
  private final Map<String, String> myRegionsData;
  private final String mySerializedRegionCodes;
  private final String mySerializedRegionDescriptions;

  public RegionData() {
    Map<String, String> allRegionsMap = Arrays.stream(Regions.values())
      .collect(Collectors.toMap(Regions::getName, Regions::getDescription));

    myRegionsData = RegionUtils.getRegionsForService(SERVICE_NAME)
      .stream()
      .map(Region::getName)
      .collect(Collectors.toMap(Function.identity(), allRegionsMap::get, (a, b) -> a, () -> new TreeMap<>(REGION_COMPARATOR)));

    mySerializedRegionCodes = Arrays.toString(myRegionsData.keySet().toArray());
    mySerializedRegionDescriptions = Arrays.toString(myRegionsData.values().toArray());
  }

  public String getSerializedRegionCodes() {
    return mySerializedRegionCodes;
  }

  public String getSerializedRegionDescriptions() {
    return mySerializedRegionDescriptions;
  }
}
