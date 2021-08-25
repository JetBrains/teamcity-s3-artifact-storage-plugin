/*
 * Copyright 2000-2021 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;

import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.artifacts.s3.cloudfront.CloudFrontConstants;
import jetbrains.buildServer.artifacts.s3.util.ParamUtil;
import jetbrains.buildServer.artifacts.s3.BucketLocationFetcher;
import jetbrains.buildServer.artifacts.s3.web.S3StoragePropertiesUtil;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.artifacts.ArtifactStorageType;
import jetbrains.buildServer.serverSide.artifacts.ArtifactStorageTypeRegistry;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import jetbrains.buildServer.util.amazon.AWSRegions;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_USE_PRE_SIGNED_URL_FOR_UPLOAD;
import static jetbrains.buildServer.artifacts.s3.cloudfront.CloudFrontConstants.*;
import static jetbrains.buildServer.util.amazon.AWSCommonParams.REGION_NAME_PARAM;
import static jetbrains.buildServer.util.amazon.AWSCommonParams.SECURE_SECRET_ACCESS_KEY_PARAM;

/**
 * Created by Nikita.Skvortsov
 * date: 24.02.2016.
 */
public class S3StorageType extends ArtifactStorageType {

  @NotNull private final String mySettingsJSP;
  @NotNull private final ServerSettings myServerSettings;
  @NotNull private final ServerPaths myServerPaths;

  public S3StorageType(@NotNull ArtifactStorageTypeRegistry registry,
                       @NotNull PluginDescriptor descriptor,
                       @NotNull ServerSettings serverSettings,
                       @NotNull ServerPaths serverPaths) {
    mySettingsJSP = descriptor.getPluginResourcesPath(S3Constants.S3_SETTINGS_PATH + ".jsp");
    myServerSettings = serverSettings;
    myServerPaths = serverPaths;
    registry.registerStorageType(this);
  }

  @NotNull
  @Override
  public String getType() {
    return S3Constants.S3_STORAGE_TYPE;
  }

  @NotNull
  @Override
  public String getName() {
    return "S3 Storage";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Uses s3 bucket to store build artifacts";
  }

  @NotNull
  @Override
  public String getEditStorageParametersPath() {
    return mySettingsJSP;
  }

  @Nullable
  @Override
  public Map<String, String> getDefaultParameters() {
    Map<String, String> result = new HashMap<>(AWSCommonParams.getDefaults(myServerSettings.getServerUUID()));
    result.put(AWSCommonParams.REGION_NAME_PARAM, AWSRegions.DEFAULT_REGION);
    result.put(S3Constants.S3_USE_PRE_SIGNED_URL_FOR_UPLOAD, Boolean.toString(true));
    return result;
  }

  @Nullable
  @Override
  public PropertiesProcessor getParametersProcessor() {
    return params -> {
      S3StoragePropertiesUtil.processProperties(params);
      final ArrayList<InvalidProperty> invalids = new ArrayList<>();
      for (Map.Entry<String, String> e : S3Util.validateParameters(params, true).entrySet()) {
        invalids.add(new InvalidProperty(e.getKey(), e.getValue()));
      }

      final String bucketName = S3Util.getBucketName(params);
      if (bucketName != null) {
        try {
          final String location = S3Util.withS3ClientShuttingDownImmediately(
            ParamUtil.putSslValues(myServerPaths, params),
            client -> S3Util.withClientCorrectingRegion(client, params, correctedClient -> correctedClient.getBucketLocation(bucketName))
          );
          if (location == null) {
            invalids.add(new InvalidProperty(S3Util.beanPropertyNameForBucketName(), "Bucket does not exist"));
          } else {
            if (TeamCityProperties.getBooleanOrTrue("teamcity.internal.storage.s3.autoCorrectRegion") && !location.equalsIgnoreCase(params.get(REGION_NAME_PARAM))) {
              params.put(REGION_NAME_PARAM, BucketLocationFetcher.getRegionName(location));
            }
          }
        } catch (Throwable e) {
          invalids.add(new InvalidProperty(S3Util.beanPropertyNameForBucketName(), e.getMessage()));
        }

        Distribution distribution;

        if (TeamCityProperties.getBoolean(S3_ENABLE_CLOUDFRONT_INTEGRATION) && S3Util.getCloudFrontEnabled(params)) {
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
        }
      }

      return invalids;
    };
  }

  private Distribution createDistribution(Map<String, String> params, ArrayList<InvalidProperty> invalids) {
    return AWSCommonParams.withAWSClients(params, clients -> {
      AmazonCloudFront cloudFrontClient = clients.createCloudFrontClient();
      String keyGroupId = null;

      try {
        String bucketName = S3Util.getBucketName(params);
        String originId = bucketName + "." + UUID.randomUUID();

        CachePolicy defaultPolicy = cloudFrontClient.listCachePolicies(new ListCachePoliciesRequest())
          .getCachePolicyList().getItems().stream()
          .map(CachePolicySummary::getCachePolicy)
          .filter(p -> p.getCachePolicyConfig().getName().equals(CloudFrontConstants.S3_CLOUDFRONT_CACHE_POLICY))
          .findFirst()
          .orElseGet(() -> cloudFrontClient.listCachePolicies(new ListCachePoliciesRequest())
            .getCachePolicyList().getItems().get(0).getCachePolicy());

        String oaiId = null;
        AmazonS3 s3Client = clients.createS3Client();
        BucketPolicy bucketPolicy = s3Client.getBucketPolicy(bucketName);
        Policy policy;
        if (bucketPolicy.getPolicyText() == null) {
          policy = new Policy();
        } else {
          policy = Policy.fromJson(bucketPolicy.getPolicyText());
        }
        Optional<Statement> first = policy.getStatements().stream().filter(
          st -> st.getPrincipals().stream().anyMatch(p -> p.getProvider().equals("AWS") && p.getId().contains(S3_CLOUDFRONT_OAI_SUBSTRING))).findFirst();
        if (first.isPresent()) {
          Optional<Principal> principal =
            first.get().getPrincipals().stream().filter(p -> p.getProvider().equals("AWS") && p.getId().contains(S3_CLOUDFRONT_OAI_SUBSTRING)).findFirst();

          if (principal.isPresent()) {
            String pattern = S3_CLOUDFRONT_OAI_SUBSTRING;
            String principalId = principal.get().getId();
            int position = principalId.indexOf(pattern);

            oaiId = principalId.substring(position + pattern.length());
          }
        }
        if (oaiId == null) {
          String oaiIdentifier = "teamcity." + bucketName + ".access";
          List<CloudFrontOriginAccessIdentitySummary> oaiList =
            cloudFrontClient.listCloudFrontOriginAccessIdentities(new ListCloudFrontOriginAccessIdentitiesRequest())
              .getCloudFrontOriginAccessIdentityList()
              .getItems();

          oaiId = oaiList.stream()
            .filter(o -> o.getComment().equals(oaiIdentifier))
            .findFirst()
            .map(o -> o.getId())
            .orElseGet(() -> createOriginAccessIdentity(cloudFrontClient, oaiIdentifier));
        } else {
          CloudFrontOriginAccessIdentity identity =
            cloudFrontClient.getCloudFrontOriginAccessIdentity(new GetCloudFrontOriginAccessIdentityRequest().withId(oaiId)).getCloudFrontOriginAccessIdentity();

          oaiId = identity.getId();
        }

        if (policy.getStatements().isEmpty()) {
          Collection<Statement> statements = policy.getStatements();
          statements.add(generateStatementForOAI(bucketName, oaiId));
          policy.setStatements(statements);
          s3Client.setBucketPolicy(bucketName, policy.toJson());
        }

        Origin origin = new Origin()
          .withS3OriginConfig(new S3OriginConfig().withOriginAccessIdentity(S3_CLOUDFRONT_OAI_ID_TEMPLATE + oaiId))
          .withDomainName(bucketName + "." + S3Constants.S3_ADDRESS)
          .withId(originId);

        ListKeyGroupsResult keyGroups = cloudFrontClient.listKeyGroups(new ListKeyGroupsRequest());

        keyGroupId = keyGroups.getKeyGroupList().getItems().stream()
          .filter(gr -> gr.getKeyGroup().getKeyGroupConfig().getItems().contains(S3Util.getCloudFrontPublicKeyId(params)))
          .findFirst()
          .map(KeyGroupSummary::getKeyGroup)
          .map(KeyGroup::getId)
          .orElseGet(() -> createKeyGroup(cloudFrontClient, params));

        TrustedKeyGroups trustedKeyGroups = new TrustedKeyGroups()
          .withQuantity(1)
          .withEnabled(true)
          .withItems(keyGroupId);
        DefaultCacheBehavior cacheBehavior = new DefaultCacheBehavior()
          .withViewerProtocolPolicy(ViewerProtocolPolicy.RedirectToHttps)
          .withTargetOriginId(originId)
          .withCachePolicyId(defaultPolicy.getId())
          .withTrustedKeyGroups(trustedKeyGroups);
        DistributionConfig distributionConfig = new DistributionConfig()
          .withDefaultCacheBehavior(cacheBehavior)
          .withOrigins(new Origins().withItems(origin).withQuantity(1))
          .withCallerReference(ZonedDateTime.now(ZoneOffset.UTC).toString())
          .withComment("Created by TeamCity")
          .withEnabled(true);
        CreateDistributionResult result = cloudFrontClient.createDistribution(new CreateDistributionRequest(distributionConfig));
        return result.getDistribution();
      } catch (SdkClientException e) {
        if (keyGroupId != null) {
          cloudFrontClient.deleteKeyGroup(new DeleteKeyGroupRequest().withId(keyGroupId));
        }
        invalids.add(new InvalidProperty(CloudFrontConstants.S3_CLOUDFRONT_DISTRIBUTION, e.getMessage()));
        return null;
      }
    });
  }

  private Statement generateStatementForOAI(String bucketName, String id) {
    return new Statement(Statement.Effect.Allow)
      .withActions(S3Actions.GetObject)
      .withPrincipals(new Principal(S3_CLOUDFRONT_PRINCIPAL_TEMPLATE + id))
      .withResources(new S3ObjectResource(bucketName, "*"));
  }

  private String createOriginAccessIdentity(AmazonCloudFront cloudFrontClient, String oaiIdentifier) {
    CloudFrontOriginAccessIdentityConfig config = new CloudFrontOriginAccessIdentityConfig().withCallerReference(oaiIdentifier).withComment(oaiIdentifier);

    CreateCloudFrontOriginAccessIdentityRequest request = new CreateCloudFrontOriginAccessIdentityRequest().withCloudFrontOriginAccessIdentityConfig(config);
    CloudFrontOriginAccessIdentity identity = cloudFrontClient.createCloudFrontOriginAccessIdentity(request).getCloudFrontOriginAccessIdentity();

    return identity.getId();
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
        GetDistributionResult result = cloudFrontClient.getDistribution(new GetDistributionRequest(selectedDistribution));
        return result.getDistribution();
      } catch (NoSuchDistributionException e) {
        invalids.add(new InvalidProperty(CloudFrontConstants.S3_CLOUDFRONT_DISTRIBUTION, e.getMessage()));
        return null;
      }
    });
  }

  @NotNull
  @Override
  public SettingsPreprocessor getSettingsPreprocessor() {
    return input -> {
      final Map<String, String> output = new HashMap<>(input);
      if (Boolean.parseBoolean(input.get(S3_USE_PRE_SIGNED_URL_FOR_UPLOAD))) {
        output.remove(SECURE_SECRET_ACCESS_KEY_PARAM);
      }
      return output;
    };
  }
}
