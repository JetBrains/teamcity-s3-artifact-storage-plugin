package jetbrains.buildServer.artifacts.s3;

import com.amazonaws.services.cloudfront.model.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.*;
import jetbrains.buildServer.Used;
import org.jetbrains.annotations.NotNull;

public class ListCloudFrontDistributionsFetcher extends S3ClientResourceFetcher<ListCloudFrontDistributionsFetcher.ListDistributionsDto> {

  @Override
  protected ListDistributionsDto fetchDto(Map<String, String> parameters) {
    String bucketName = S3Util.getBucketName(parameters);

    if (bucketName == null) {
      throw new IllegalArgumentException("No S3 bucket specified");
    }

    return S3Util.withCloudFrontClient(parameters, client -> {
      ListDistributionsRequest request = new ListDistributionsRequest();
      ListDistributionsResult result = client.listDistributions(request);

      List<KeyGroupSummary> keyGroups = client.listKeyGroups(new ListKeyGroupsRequest())
                                              .getKeyGroupList()
                                              .getItems();

      Map<String, KeyGroup> groupMap = keyGroups
        .stream()
        .map(KeyGroupSummary::getKeyGroup)
        .collect(Collectors.toMap(KeyGroup::getId, Function.identity()));

      List<DistributionDto> distributions = result
        .getDistributionList()
        .getItems()
        .stream()
        .filter(d -> d.getOrigins()
                      .getItems()
                      .stream()
                      .anyMatch(o -> o.getDomainName().equals(bucketName + "." + S3Constants.S3_ADDRESS))
        )
        .map(d -> {
          String id = d.getId();
          String comment = d.getComment();
          Boolean enabled = d.isEnabled();
          Set<String> publicKeys = new HashSet<>();
          TrustedKeyGroups defaultKeyGroups = d.getDefaultCacheBehavior().getTrustedKeyGroups();
          publicKeys.addAll(getAllPublicKeys(groupMap, defaultKeyGroups));
          for (CacheBehavior item : d.getCacheBehaviors().getItems()) {
            publicKeys.addAll(getAllPublicKeys(groupMap, item.getTrustedKeyGroups()));
          }
          return new DistributionDto(id, comment, enabled, publicKeys);
        })
        .collect(Collectors.toList());
      return new ListDistributionsDto(distributions);
    });
  }

  private Set<String> getAllPublicKeys(Map<String, KeyGroup> groupMap, TrustedKeyGroups keyGroups) {
    HashSet<String> publicKeys = new HashSet<>();
    for (String keyGroupId : keyGroups.getItems()) {
      KeyGroup keyGroup = groupMap.get(keyGroupId);
      publicKeys.addAll(keyGroup.getKeyGroupConfig().getItems());
    }

    return publicKeys;
  }

  @XmlRootElement(name = "distributions")
  @XmlAccessorType(XmlAccessType.PROPERTY)
  public static class ListDistributionsDto implements S3Dto {
    @NotNull
    private final List<DistributionDto> distributions;

    @Used("xml-serialization")
    public ListDistributionsDto() {
      distributions = new ArrayList<>();
    }

    public ListDistributionsDto(@NotNull final List<DistributionDto> distributions) {
      this.distributions = distributions;
    }

    @XmlElement(name = "distribution")
    @XmlList
    @NotNull
    public List<DistributionDto> getDistributions() {
      return distributions;
    }
  }

  @XmlRootElement(name = "distribution")
  public static class DistributionDto {
    @NotNull
    private final String id;
    @NotNull
    private final String description;
    @NotNull
    private final Collection<String> publicKeys;

    private final boolean enabled;

    @Used("xml-serialization")
    public DistributionDto(@NotNull String id, @NotNull String description, @NotNull Boolean enabled, @NotNull Collection<String> publicKeys) {
      this.id = id;
      this.description = description;
      this.enabled = enabled;
      this.publicKeys = publicKeys;
    }

    @NotNull
    @XmlElement(name = "id")
    public String getId() {
      return id;
    }

    @NotNull
    @XmlElement(name = "description")
    public String getDescription() {
      return description;
    }

    @XmlElement(name = "enabled")
    public boolean isEnabled() {
      return enabled;
    }

    @NotNull
    @XmlElement(name = "publicKey")
    public Collection<String> getPublicKeys() {
      return publicKeys;
    }
  }
}
