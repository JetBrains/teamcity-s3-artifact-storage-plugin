package jetbrains.buildServer.artifacts.s3;

import software.amazon.awssdk.services.cloudfront.model.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.*;
import jetbrains.buildServer.Used;
import jetbrains.buildServer.artifacts.s3.amazonClient.AmazonS3Provider;
import jetbrains.buildServer.artifacts.s3.cloudfront.CloudFrontConstants;
import jetbrains.buildServer.serverSide.connections.credentials.ConnectionCredentialsException;
import org.jetbrains.annotations.NotNull;

public class ListCloudFrontDistributionsFetcher extends S3ClientResourceFetcher<ListCloudFrontDistributionsFetcher.ListDistributionsDto> {
  private final AmazonS3Provider myAmazonS3Builder;

  public ListCloudFrontDistributionsFetcher(@NotNull AmazonS3Provider amazonS3Builder) {
    myAmazonS3Builder = amazonS3Builder;
  }

  @Override
  protected ListDistributionsDto fetchCurrentValue(Map<String, String> parameters, @NotNull String projectId) throws Exception {
    final String publicKeyId = S3Util.getCloudFrontPublicKeyId(parameters);

    return myAmazonS3Builder.withCloudFrontClient(parameters, projectId, client -> {
      final String uploadDistr = S3Util.getCloudFrontUploadDistribution(parameters);

      final List<DistributionDto> distributionDtos = new ArrayList<>();

      if (uploadDistr != null) {
        final Distribution distribution = client.getDistribution(b -> b.id(uploadDistr)).distribution();
        distributionDtos.add(toDto(distribution, publicKeyId));
      }

      final String downloadDistr = S3Util.getCloudFrontDownloadDistribution(parameters);

      if (downloadDistr != null) {
        final Distribution distribution = client.getDistribution(b -> b.id(downloadDistr)).distribution();
        distributionDtos.add(toDto(distribution, publicKeyId));
      }

      return new ListDistributionsDto(distributionDtos);
    });
  }

  @NotNull
  private static DistributionDto toDto(Distribution distribution, String publicKeyId) {
    final String id = distribution.id();
    final String comment = distribution.distributionConfig().comment();
    final Boolean enabled = distribution.distributionConfig().enabled();
    return new DistributionDto(id, comment, enabled, Collections.singletonList(publicKeyId));
  }

  @Override
  protected ListDistributionsDto fetchDto(Map<String, String> parameters, @NotNull String projectId) throws ConnectionCredentialsException {
    String bucketName = S3Util.getBucketName(parameters);

    if (bucketName == null) {
      throw new IllegalArgumentException("No S3 bucket specified");
    }

    String bucketRegion = BucketLocationFetcher.getRegionName(myAmazonS3Builder.withCorrectingRegionAndAcceleration(
      parameters,
      projectId,
      correctedClient -> correctedClient.headBucket(b -> b.bucket(bucketName)).bucketRegion(),
      true
    ));

    String domainPattern = String.format(CloudFrontConstants.S3_BUCKET_DOMAIN_PATTERN, bucketName, bucketRegion);
    String domainPatternNoRegion = String.format(CloudFrontConstants.S3_BUCKET_DOMAIN_PATTERN_NO_REGION, bucketName);

    return myAmazonS3Builder.withCloudFrontClient(parameters, projectId, client -> {
      final List<DistributionSummary> distributionSummaries = new LinkedList<>();
      DistributionList distributionsList;
      String marker = null;
      do {
        ListDistributionsRequest.Builder requestBuilder = ListDistributionsRequest.builder().maxItems("1000").marker(marker);
        distributionsList = client.listDistributions(requestBuilder.build()).distributionList();
        distributionSummaries.addAll(distributionsList.items());
        marker = distributionsList.nextMarker();
      } while (marker != null);

      final List<KeyGroupSummary> keyGroups = new LinkedList<>();
      KeyGroupList keyGroupList;
      do {
        ListKeyGroupsRequest.Builder requestBuilder = ListKeyGroupsRequest.builder().maxItems("1000").marker(marker);
        keyGroupList = client.listKeyGroups(requestBuilder.build()).keyGroupList();
        keyGroups.addAll(keyGroupList.items());
        marker = keyGroupList.nextMarker();
      } while (marker != null);

      Map<String, KeyGroup> groupMap = keyGroups
        .stream()
        .map(KeyGroupSummary::keyGroup)
        .collect(Collectors.toMap(KeyGroup::id, Function.identity()));

      List<DistributionDto> distributions = distributionSummaries
        .stream()
        .filter(d -> d.origins()
                      .items()
                      .stream()
                      .anyMatch(o -> o.domainName().equals(domainPattern) || o.domainName().equals(domainPatternNoRegion))
        ).map(d -> {
          String id = d.id();
          String comment = d.comment();
          Boolean enabled = d.enabled();
          Set<String> publicKeys = new HashSet<>();
          TrustedKeyGroups defaultKeyGroups = d.defaultCacheBehavior().trustedKeyGroups();
          publicKeys.addAll(getAllPublicKeys(groupMap, defaultKeyGroups));
          for (CacheBehavior item : d.cacheBehaviors().items()) {
            publicKeys.addAll(getAllPublicKeys(groupMap, item.trustedKeyGroups()));
          }
          return new DistributionDto(id, comment, enabled, publicKeys);
        })
        .collect(Collectors.toList());
      return new ListDistributionsDto(distributions);
    });
  }

  private Set<String> getAllPublicKeys(Map<String, KeyGroup> groupMap, TrustedKeyGroups keyGroups) {
    HashSet<String> publicKeys = new HashSet<>();
    for (String keyGroupId : keyGroups.items()) {
      KeyGroup keyGroup = groupMap.get(keyGroupId);
      publicKeys.addAll(keyGroup.keyGroupConfig().items());
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
