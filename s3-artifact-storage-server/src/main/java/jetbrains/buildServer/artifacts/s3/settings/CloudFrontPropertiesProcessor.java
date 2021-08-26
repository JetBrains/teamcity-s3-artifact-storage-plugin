package jetbrains.buildServer.artifacts.s3.settings;

import com.amazonaws.SdkClientException;
import com.amazonaws.auth.policy.Policy;
import com.amazonaws.auth.policy.Principal;
import com.amazonaws.auth.policy.Statement;
import com.amazonaws.auth.policy.actions.S3Actions;
import com.amazonaws.auth.policy.resources.S3ObjectResource;
import com.amazonaws.services.cloudfront.AmazonCloudFront;
import com.amazonaws.services.cloudfront.model.*;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.BucketPolicy;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.artifacts.s3.cloudfront.CloudFrontConstants;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import org.jetbrains.annotations.Nullable;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static jetbrains.buildServer.artifacts.s3.cloudfront.CloudFrontConstants.*;

public class CloudFrontPropertiesProcessor implements PropertiesProcessor {

  @Override
  public Collection<InvalidProperty> process(Map<String, String> params) {
    Distribution distribution;
    final ArrayList<InvalidProperty> invalids = new ArrayList<>();

    if (S3Util.getCloudFrontDistribution(params) == null) {
      distribution = createDistribution(params, invalids);
      if (distribution != null) {
        params.put(CloudFrontConstants.S3_CLOUDFRONT_DISTRIBUTION, distribution.getId());
      }
    } else {
      distribution = getDistribution(params, invalids);
    }
    if (distribution != null) {
      params.put(CloudFrontConstants.S3_CLOUDFRONT_DOMAIN, distribution.getDomainName());
    }

    return invalids;
  }


  private Distribution createDistribution(Map<String, String> params, ArrayList<InvalidProperty> invalids) {
    return AWSCommonParams.withAWSClients(params, clients -> {
      AmazonCloudFront cloudFrontClient = clients.createCloudFrontClient();
      AmazonS3 s3Client = clients.createS3Client();

      String keyGroupId = null;
      boolean keyGroupCreated = false;
      try {
        String bucketName = S3Util.getBucketName(params);
        String originId = bucketName + "." + UUID.randomUUID();

        String oaiId = getOriginAccessIdentityId(s3Client, cloudFrontClient, bucketName);

        Origin origin = createOrigin(bucketName, originId, oaiId);

        keyGroupId = getKeyGroupId(params, cloudFrontClient);
        if (keyGroupId == null) {
          keyGroupId = createKeyGroup(cloudFrontClient, params);
          keyGroupCreated = true;
        }

        DistributionConfig distributionConfig = createDistributionConfig(cloudFrontClient, keyGroupId, originId, origin);
        CreateDistributionResult result = cloudFrontClient.createDistribution(new CreateDistributionRequest(distributionConfig));
        return result.getDistribution();
      } catch (SdkClientException e) {
        if (keyGroupId != null && keyGroupCreated) {
          cloudFrontClient.deleteKeyGroup(new DeleteKeyGroupRequest().withId(keyGroupId));
        }
        invalids.add(new InvalidProperty(CloudFrontConstants.S3_CLOUDFRONT_DISTRIBUTION, e.getMessage()));
        return null;
      }
    });
  }

  private DistributionConfig createDistributionConfig(AmazonCloudFront cloudFrontClient, String keyGroupId, String originId, Origin origin) {
    DefaultCacheBehavior cacheBehavior = createDefaultCacheBehavior(cloudFrontClient, keyGroupId, originId);

    return new DistributionConfig()
      .withDefaultCacheBehavior(cacheBehavior)
      .withOrigins(new Origins().withItems(origin).withQuantity(1))
      .withCallerReference(ZonedDateTime.now(ZoneOffset.UTC).toString())
      .withComment("Created by TeamCity")
      .withEnabled(true);
  }

  private Origin createOrigin(String bucketName, String originId, String originAccessIdentityId) {

    String oaiId = String.format(S3_CLOUDFRONT_OAI_ID_TEMPLATE, originAccessIdentityId);
    return new Origin()
      .withS3OriginConfig(new S3OriginConfig().withOriginAccessIdentity(oaiId))
      .withDomainName(bucketName + "." + S3Constants.S3_ADDRESS)
      .withId(originId);
  }

  private String getKeyGroupId(Map<String, String> params, AmazonCloudFront cloudFrontClient) {
    String cloudFrontPublicKeyId = S3Util.getCloudFrontPublicKeyId(params);

    return cloudFrontClient.listKeyGroups(new ListKeyGroupsRequest())
                           .getKeyGroupList()
                           .getItems()
                           .stream()
                           .map(KeyGroupSummary::getKeyGroup)
                           .filter(gr -> gr.getKeyGroupConfig().getItems().contains(cloudFrontPublicKeyId))
                           .findFirst()
                           .map(KeyGroup::getId)
                           .orElse(null);
  }

  private DefaultCacheBehavior createDefaultCacheBehavior(AmazonCloudFront cloudFrontClient, String keyGroupId, String originId) {
    CachePolicy defaultPolicy = getCachePolicy(cloudFrontClient);

    TrustedKeyGroups trustedKeyGroups = new TrustedKeyGroups()
      .withQuantity(1)
      .withEnabled(true)
      .withItems(keyGroupId);
    return new DefaultCacheBehavior()
      .withViewerProtocolPolicy(ViewerProtocolPolicy.RedirectToHttps)
      .withTargetOriginId(originId)
      .withCachePolicyId(defaultPolicy.getId())
      .withTrustedKeyGroups(trustedKeyGroups);
  }

  private CachePolicy getCachePolicy(AmazonCloudFront cloudFrontClient) {
    return cloudFrontClient.listCachePolicies(new ListCachePoliciesRequest())
                           .getCachePolicyList().getItems().stream()
                           .map(CachePolicySummary::getCachePolicy)
                           .filter(p -> p.getCachePolicyConfig().getName().equals(CloudFrontConstants.S3_CLOUDFRONT_CACHE_POLICY))
                           .findFirst()
                           .orElseThrow(() -> new AmazonCloudFrontException(String.format("Managed Cache policy '%s' not found", CloudFrontConstants.S3_CLOUDFRONT_CACHE_POLICY)));
  }

  @Nullable
  private String getOriginAccessIdentityId(AmazonS3 s3Client, AmazonCloudFront cloudFrontClient, String bucketName) {
    Policy policy = getPolicy(bucketName, s3Client);
    List<CloudFrontOriginAccessIdentitySummary> existingIdentities = cloudFrontClient.listCloudFrontOriginAccessIdentities(new ListCloudFrontOriginAccessIdentitiesRequest())
                                                                                     .getCloudFrontOriginAccessIdentityList()
                                                                                     .getItems();
    List<String> existingOaiIds = existingIdentities
      .stream()
      .map(CloudFrontOriginAccessIdentitySummary::getId)
      .collect(Collectors.toList());
    String oaiId = extractOriginAccessIdentity(policy, existingOaiIds);

    if (oaiId == null) {
      oaiId = getDefaultOriginAccessIdentity(cloudFrontClient, s3Client, bucketName, policy, existingIdentities);
    }
    return oaiId;
  }

  @Nullable
  private String getDefaultOriginAccessIdentity(AmazonCloudFront cloudFrontClient,
                                                AmazonS3 s3Client,
                                                String bucketName,
                                                Policy policy,
                                                List<CloudFrontOriginAccessIdentitySummary> existingIdentities) {
    String oaiId = existingIdentities.stream()
                                     .filter(o -> o.getComment().equals(S3_CLOUDFRONT_DEFAULT_OAI_COMMENT))
                                     .findFirst()
                                     .map(CloudFrontOriginAccessIdentitySummary::getId)
                                     .orElseGet(() -> createOriginAccessIdentity(cloudFrontClient, S3_CLOUDFRONT_DEFAULT_OAI_COMMENT));

    if (oaiId != null) {
      Collection<Statement> statements = policy.getStatements();
      statements.add(generateStatementForOAI(bucketName, oaiId));
      policy.setStatements(statements);
      s3Client.setBucketPolicy(bucketName, policy.toJson());
    }
    return oaiId;
  }

  private String extractOriginAccessIdentity(Policy policy, List<String> existingIds) {
    String pattern = S3_CLOUDFRONT_OAI_SUBSTRING;

    return policy.getStatements().stream()
                 .flatMap(s -> s.getPrincipals().stream())
                 .filter(p -> p.getProvider().equals("AWS"))
                 .map(Principal::getId)
                 .filter(id -> id.contains(S3_CLOUDFRONT_OAI_SUBSTRING))
                 .map(id -> id.substring(id.indexOf(pattern) + pattern.length()))
                 .filter(existingIds::contains)
                 .findAny().orElse(null);
  }

  private Policy getPolicy(String bucketName, AmazonS3 s3Client) {
    BucketPolicy bucketPolicy = s3Client.getBucketPolicy(bucketName);
    Policy policy;
    if (bucketPolicy.getPolicyText() == null) {
      policy = new Policy();
    } else {
      policy = Policy.fromJson(bucketPolicy.getPolicyText());
    }
    return policy;
  }

  private Statement generateStatementForOAI(String bucketName, String id) {
    return new Statement(Statement.Effect.Allow)
      .withActions(S3Actions.GetObject)
      .withPrincipals(new Principal(String.format(S3_CLOUDFRONT_PRINCIPAL_TEMPLATE, id)))
      .withResources(new S3ObjectResource(bucketName, "*"));
  }

  private String createOriginAccessIdentity(AmazonCloudFront cloudFrontClient, String comment) {
    CloudFrontOriginAccessIdentityConfig config = new CloudFrontOriginAccessIdentityConfig().withCallerReference(comment).withComment(comment);

    CreateCloudFrontOriginAccessIdentityRequest request = new CreateCloudFrontOriginAccessIdentityRequest()
      .withCloudFrontOriginAccessIdentityConfig(config);
    return cloudFrontClient.createCloudFrontOriginAccessIdentity(request)
                           .getCloudFrontOriginAccessIdentity()
                           .getId();
  }

  private String createKeyGroup(AmazonCloudFront cloudFrontClient, Map<String, String> params) {
    CreateKeyGroupRequest createKeyGroupRequest = new CreateKeyGroupRequest()
      .withKeyGroupConfig(new KeyGroupConfig()
                            .withName("auto_" + UUID.randomUUID())
                            .withItems(S3Util.getCloudFrontPublicKeyId(params)));
    CreateKeyGroupResult keyGroup = cloudFrontClient.createKeyGroup(createKeyGroupRequest);

    return keyGroup.getKeyGroup().getId();
  }

  private Distribution getDistribution(Map<String, String> params, ArrayList<InvalidProperty> invalids) {
    return AWSCommonParams.withAWSClients(params, clients -> {
      AmazonCloudFront cloudFrontClient = clients.createCloudFrontClient();
      String selectedDistribution = S3Util.getCloudFrontDistribution(params);

      try {
        return cloudFrontClient.getDistribution(new GetDistributionRequest(selectedDistribution))
                               .getDistribution();
      } catch (NoSuchDistributionException e) {
        invalids.add(new InvalidProperty(CloudFrontConstants.S3_CLOUDFRONT_DISTRIBUTION, e.getMessage()));
        return null;
      }
    });
  }
}
