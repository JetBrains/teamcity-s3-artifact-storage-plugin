package jetbrains.buildServer.artifacts.s3;

import java.util.*;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.*;

import jetbrains.buildServer.Used;
import jetbrains.buildServer.artifacts.s3.amazonClient.AmazonS3Provider;
import org.jetbrains.annotations.NotNull;
import software.amazon.awssdk.services.cloudfront.model.ListPublicKeysRequest;
import software.amazon.awssdk.services.cloudfront.model.PublicKey;
import software.amazon.awssdk.services.cloudfront.model.PublicKeyList;
import software.amazon.awssdk.services.cloudfront.model.PublicKeySummary;

public class ListCloudFrontPublicKeysFetcher extends S3ClientResourceFetcher<ListCloudFrontPublicKeysFetcher.ListPublicKeysDto> {

  private final AmazonS3Provider myAmazonS3Provider;
  public ListCloudFrontPublicKeysFetcher(@NotNull final AmazonS3Provider amazonS3Provider) {
    myAmazonS3Provider = amazonS3Provider;
  }

  @Override
  protected ListPublicKeysDto fetchCurrentValue(Map<String, String> parameters, @NotNull String projectId) throws Exception {
    return myAmazonS3Provider.withCloudFrontClient(parameters, projectId, client -> {
      final ArrayList<PublicKeyDto> publicKeys = new ArrayList<>();
      final String cloudFrontPublicKeyId = S3Util.getCloudFrontPublicKeyId(parameters);

      if (cloudFrontPublicKeyId != null) {
        final PublicKey publicKey = client.getPublicKey(b -> b.id(cloudFrontPublicKeyId)).publicKey();
        publicKeys.add(new PublicKeyDto(publicKey.id(), publicKey.publicKeyConfig().name()));
      }
      return new ListPublicKeysDto(publicKeys);
    });
  }

  @Override
  protected ListPublicKeysDto fetchDto(Map<String, String> parameters, @NotNull String projectId) throws Exception {
    return myAmazonS3Provider.withCloudFrontClient(parameters, projectId, client -> {
      final List<PublicKeySummary> publicKeySummaries = new LinkedList<>();
      PublicKeyList publicKeyList;
      String marker = null;
      do {
        ListPublicKeysRequest.Builder requestBuilder = ListPublicKeysRequest.builder().maxItems("1000").marker(marker);
        publicKeyList = client.listPublicKeys(requestBuilder.build()).publicKeyList();
        publicKeySummaries.addAll(publicKeyList.items());
        marker = publicKeyList.nextMarker();
      } while (marker != null);
      List<PublicKeyDto> keys = publicKeySummaries.stream()
        .map(key -> new PublicKeyDto(key.id(), key.name()))
        .collect(Collectors.toList());
      return new ListPublicKeysDto(keys);
    });
  }

  @XmlRootElement(name = "publicKeys")
  @XmlAccessorType(XmlAccessType.PROPERTY)
  public static class ListPublicKeysDto implements S3Dto {
    @NotNull
    private final List<PublicKeyDto> publicKeys;

    @Used("xml-serialization")
    public ListPublicKeysDto() {
      this.publicKeys = null;
    }

    public ListPublicKeysDto(@NotNull final List<PublicKeyDto> publicKeys) {
      this.publicKeys = publicKeys;
    }

    @XmlElement(name = "publicKey")
    @XmlList
    @NotNull
    public List<PublicKeyDto> getPublicKeys() {
      return publicKeys;
    }
  }

  @XmlRootElement(name = "publicKey")
  public static class PublicKeyDto {
    @NotNull
    private final String id;
    @NotNull
    private final String name;

    @Used("xml-serialization")
    public PublicKeyDto(@NotNull String id, @NotNull String name) {
      this.id = id;
      this.name = name;
    }

    @NotNull
    public String getId() {
      return id;
    }

    @NotNull
    public String getName() {
      return name;
    }
  }
}
