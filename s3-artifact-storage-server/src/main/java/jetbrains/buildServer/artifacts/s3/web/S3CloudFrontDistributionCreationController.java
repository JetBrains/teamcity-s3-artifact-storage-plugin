

package jetbrains.buildServer.artifacts.s3.web;

import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.io.StringWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.Used;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.artifacts.s3.amazonClient.AmazonS3Provider;
import jetbrains.buildServer.artifacts.s3.serialization.S3XmlSerializerFactory;
import jetbrains.buildServer.controllers.ActionErrors;
import jetbrains.buildServer.controllers.BaseFormXmlController;
import jetbrains.buildServer.controllers.BasePropertiesBean;
import jetbrains.buildServer.controllers.admin.projects.PluginPropertiesUtil;
import jetbrains.buildServer.serverSide.IOGuard;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.auth.AccessChecker;
import jetbrains.buildServer.serverSide.connections.credentials.ConnectionCredentialsException;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.ModelAndView;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.policybuilder.iam.IamEffect;
import software.amazon.awssdk.policybuilder.iam.IamPolicy;
import software.amazon.awssdk.policybuilder.iam.IamPrincipal;
import software.amazon.awssdk.policybuilder.iam.IamPrincipalType;
import software.amazon.awssdk.policybuilder.iam.IamResource;
import software.amazon.awssdk.policybuilder.iam.IamStatement;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.AllowedMethods;
import software.amazon.awssdk.services.cloudfront.model.CachePolicy;
import software.amazon.awssdk.services.cloudfront.model.CachePolicyCookieBehavior;
import software.amazon.awssdk.services.cloudfront.model.CachePolicyCookiesConfig;
import software.amazon.awssdk.services.cloudfront.model.CachePolicyHeaderBehavior;
import software.amazon.awssdk.services.cloudfront.model.CachePolicyHeadersConfig;
import software.amazon.awssdk.services.cloudfront.model.CachePolicyList;
import software.amazon.awssdk.services.cloudfront.model.CachePolicyQueryStringBehavior;
import software.amazon.awssdk.services.cloudfront.model.CachePolicyQueryStringsConfig;
import software.amazon.awssdk.services.cloudfront.model.CachePolicySummary;
import software.amazon.awssdk.services.cloudfront.model.CloudFrontException;
import software.amazon.awssdk.services.cloudfront.model.CloudFrontOriginAccessIdentityConfig;
import software.amazon.awssdk.services.cloudfront.model.CloudFrontOriginAccessIdentityList;
import software.amazon.awssdk.services.cloudfront.model.CloudFrontOriginAccessIdentitySummary;
import software.amazon.awssdk.services.cloudfront.model.CreateCachePolicyRequest;
import software.amazon.awssdk.services.cloudfront.model.CreateCloudFrontOriginAccessIdentityRequest;
import software.amazon.awssdk.services.cloudfront.model.CreateDistributionRequest;
import software.amazon.awssdk.services.cloudfront.model.CreateDistributionResponse;
import software.amazon.awssdk.services.cloudfront.model.CreateKeyGroupRequest;
import software.amazon.awssdk.services.cloudfront.model.CreateKeyGroupResponse;
import software.amazon.awssdk.services.cloudfront.model.CreatePublicKeyRequest;
import software.amazon.awssdk.services.cloudfront.model.CreatePublicKeyResponse;
import software.amazon.awssdk.services.cloudfront.model.DefaultCacheBehavior;
import software.amazon.awssdk.services.cloudfront.model.DeleteKeyGroupRequest;
import software.amazon.awssdk.services.cloudfront.model.DeletePublicKeyRequest;
import software.amazon.awssdk.services.cloudfront.model.Distribution;
import software.amazon.awssdk.services.cloudfront.model.DistributionConfig;
import software.amazon.awssdk.services.cloudfront.model.DistributionList;
import software.amazon.awssdk.services.cloudfront.model.KeyGroupConfig;
import software.amazon.awssdk.services.cloudfront.model.ListCachePoliciesRequest;
import software.amazon.awssdk.services.cloudfront.model.ListCloudFrontOriginAccessIdentitiesRequest;
import software.amazon.awssdk.services.cloudfront.model.ListDistributionsRequest;
import software.amazon.awssdk.services.cloudfront.model.Method;
import software.amazon.awssdk.services.cloudfront.model.Origin;
import software.amazon.awssdk.services.cloudfront.model.Origins;
import software.amazon.awssdk.services.cloudfront.model.ParametersInCacheKeyAndForwardedToOrigin;
import software.amazon.awssdk.services.cloudfront.model.PublicKeyConfig;
import software.amazon.awssdk.services.cloudfront.model.S3OriginConfig;
import software.amazon.awssdk.services.cloudfront.model.TrustedKeyGroups;
import software.amazon.awssdk.services.cloudfront.model.ViewerProtocolPolicy;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetBucketPolicyResponse;

import static jetbrains.buildServer.artifacts.s3.cloudfront.CloudFrontConstants.*;

public class S3CloudFrontDistributionCreationController extends BaseFormXmlController {
  private static final Logger LOG = Logger.getInstance(S3CloudFrontDistributionCreationController.class.getName());

  public static final String OAI_RESOURCE_BUCKET_TEMPLATE = "arn:aws:s3:::%s/*";
  public static final String BASE_COMMENT = "Created by TeamCity";
  public static final String COMMENT = BASE_COMMENT + " for '%s'";
  public static final String NUMBERED_COMMENT = COMMENT + " (%d)";
  public static final AllowedMethods ALL_METHODS_ALLOWED = AllowedMethods.builder().items(Method.knownValues()).quantity(7).build();
  public static final AllowedMethods ONLY_DOWNLOAD_METHODS_ALLOWED = AllowedMethods.builder().items(Method.HEAD, Method.GET, Method.OPTIONS).quantity(3).build();
  public static final Predicate<CachePolicy> IS_GENERATED_POLICY = p -> p.cachePolicyConfig().name().equals(S3_CLOUDFRONT_GENERATED_CACHE_POLICY);
  public static final Predicate<CachePolicy> IS_DEFAULT_POLICY = p -> p.cachePolicyConfig().name().equals(S3_CLOUDFRONT_DEFAULT_CACHE_POLICY);

  @NotNull
  private final ProjectManager myProjectManager;
  private final AccessChecker myAccessChecker;
  @NotNull
  private final AmazonS3Provider myAmazonS3Provider;

  public S3CloudFrontDistributionCreationController(@NotNull final PluginDescriptor descriptor,
                                                    @NotNull WebControllerManager controllerManager,
                                                    @NotNull ProjectManager projectManager,
                                                    @NotNull AccessChecker accessChecker,
                                                    @NotNull AmazonS3Provider amazonS3Provider) {
    myAmazonS3Provider = amazonS3Provider;
    myProjectManager = projectManager;
    myAccessChecker = accessChecker;
    final String path = descriptor.getPluginResourcesPath("cloudFront/createDistribution.html");
    controllerManager.registerController(path, this);
    Provider provider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
    if (provider == null) {
      Security.addProvider(new BouncyCastleProvider());
    }
  }

  @Override
  @Nullable
  protected ModelAndView doGet(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) {
    return null;
  }

  @Override
  protected void doPost(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Element xmlResponse) {
    final BasePropertiesBean bean = new BasePropertiesBean(null);
    PluginPropertiesUtil.bindPropertiesFromRequest(request, bean);
    Map<String, String> params = bean.getProperties();
    String projectId = request.getParameter("projectId");

    final ActionErrors errors = new ActionErrors();

    SProject project = myProjectManager.findProjectByExternalId(projectId);

    if (project == null) {
      errors.addError(S3_CLOUDFRONT_CREATE_DISTRIBUTIONS, String.format("Project %s not found", projectId));
    } else {
      myAccessChecker.checkCanEditProject(project);

      String projectName = project.getName();

      IOGuard.allowNetworkCall(() -> {
        try {
          KeyPair keyPair = generateKeyPair();
          String bucketName = S3Util.getBucketName(params);

          if (keyPair.getPrivate() != null && keyPair.getPublic() != null && bucketName != null) {
            String privateKey = toPemString("PRIVATE KEY", keyPair.getPrivate().getEncoded());
            String publicKey = toPemString("PUBLIC KEY", keyPair.getPublic().getEncoded());

            DistributionCreationResultDTO distributionCreationResultDTO = myAmazonS3Provider.withCloudFrontClient(params, projectId, cloudFrontClient -> {
              return myAmazonS3Provider.withS3Client(params, projectId, s3Client -> {
                  String comment;

                  long distrCount = 0;
                  DistributionList distributionsList;
                  String marker = null;
                  do {
                    ListDistributionsRequest.Builder requestBuilder = ListDistributionsRequest.builder().maxItems("1000").marker(marker);
                    distributionsList = cloudFrontClient.listDistributions(requestBuilder.build()).distributionList();
                    distrCount += distributionsList.items()
                                                   .stream()
                                                   .filter(d -> d.comment() != null && d.comment().startsWith(String.format(COMMENT, projectName)))
                                                   .count();
                    marker = distributionsList.nextMarker();
                  } while ( marker != null);
                  if (distrCount > 0) {
                    comment = String.format(NUMBERED_COMMENT, projectName, distrCount);
                  } else {
                    comment = String.format(COMMENT, projectName);
                  }

                  String name = "generated_" + UUID.randomUUID().toString().substring(0, 8);
                  CreatePublicKeyResponse publicKeyResult = null;
                  CreateKeyGroupResponse keyGroupResult = null;
                  String publicKeyId = null;
                  String keyGroupId = null;
                  try {
                    publicKeyResult = uploadPublicKey(publicKey, name, comment, cloudFrontClient);
                    publicKeyId = publicKeyResult.publicKey().id();
                    keyGroupResult = createKeyGroup(publicKeyId, name, comment, cloudFrontClient);
                    keyGroupId = keyGroupResult.keyGroup().id();
                    Distribution uploadDistribution = createDistribution(keyGroupId, comment, bucketName, cloudFrontClient, s3Client, true);
                    final DistributionDTO uploadDTO = new DistributionDTO(uploadDistribution.id(), uploadDistribution.distributionConfig().comment());

                    Distribution downloadDistribution = createDistribution(keyGroupId, comment, bucketName, cloudFrontClient, s3Client, false);
                    final DistributionDTO downloadDTO = new DistributionDTO(downloadDistribution.id(), downloadDistribution.distributionConfig().comment());
                    return new DistributionCreationResultDTO(uploadDTO, downloadDTO, publicKeyId, name, privateKey);
                  } catch (SdkClientException e) {
                    if (keyGroupResult != null) {
                      try {
                        cloudFrontClient.deleteKeyGroup(DeleteKeyGroupRequest.builder()
                          .id(keyGroupId)
                          .ifMatch(keyGroupResult.eTag())
                          .build());
                      } catch (SdkClientException clientException) {
                        LOG.warnAndDebugDetails("Encountered exception while trying to delete CloudFront key group", clientException);
                      }
                    }
                    if (publicKeyResult != null) {
                      try {
                        cloudFrontClient.deletePublicKey(DeletePublicKeyRequest.builder()
                          .id(publicKeyId)
                          .ifMatch(publicKeyResult.eTag())
                          .build());
                      } catch (SdkClientException clientException) {
                        LOG.warnAndDebugDetails("Encountered exception while trying to delete CloudFront public key", clientException);
                      }
                    }

                    throw e;
                  }
              });
            });
            if (distributionCreationResultDTO != null) {
              Element element = S3XmlSerializerFactory.getInstance().serializeAsElement(distributionCreationResultDTO);
              xmlResponse.addContent(element);
            }
          }
        } catch (IllegalArgumentException | SdkClientException | IOException | NoSuchAlgorithmException | ConnectionCredentialsException e) {
          errors.addException(S3_CLOUDFRONT_CREATE_DISTRIBUTIONS, e);
        }
      });
    }

    errors.serialize(xmlResponse);
  }

  @NotNull
  private String toPemString(@NotNull String header, @NotNull byte[] key) throws IOException {
    PemObject pemObject = new PemObject(header, key);
    StringWriter writer = new StringWriter();
    PemWriter pemWriter = new PemWriter(writer);
    pemWriter.writeObject(pemObject);
    pemWriter.flush();
    return writer.toString();
  }

  @NotNull
  private Distribution createDistribution(@NotNull String keyGroupId,
                                          @NotNull String comment,
                                          @NotNull String bucketName,
                                          @NotNull CloudFrontClient cloudFrontClient,
                                          @NotNull S3Client s3Client,
                                          boolean uploadAllowed) {
    String originId = bucketName + "." + UUID.randomUUID();

    String oaiId = getOriginAccessIdentityId(s3Client, cloudFrontClient, bucketName);
    String bucketRegion = s3Client.headBucket(builder -> builder.bucket(bucketName))
      .bucketRegion();
    Origin origin = createOrigin(bucketName, bucketRegion, originId, oaiId);
    DistributionConfig distributionConfig = createDistributionConfig(cloudFrontClient, keyGroupId, origin, comment, uploadAllowed);
    CreateDistributionResponse result = cloudFrontClient.createDistribution(
      CreateDistributionRequest.builder()
        .distributionConfig(distributionConfig)
        .build()
    );
    return result.distribution();
  }

  @NotNull
  private CreateKeyGroupResponse createKeyGroup(@NotNull String publicKeyId, @NotNull String name, @NotNull String comment, @NotNull CloudFrontClient cloudFrontClient) {
    CreateKeyGroupRequest createKeyGroupRequest = CreateKeyGroupRequest.builder()
      .keyGroupConfig(
        KeyGroupConfig.builder()
          .name(name)
          .comment(comment)
          .items(publicKeyId)
          .build()
      )
      .build();

    return cloudFrontClient.createKeyGroup(createKeyGroupRequest);
  }

  @NotNull
  private CreatePublicKeyResponse uploadPublicKey(@NotNull String publicKey, @NotNull String name, @NotNull String comment, @NotNull CloudFrontClient cloudFrontClient) {
    PublicKeyConfig config = PublicKeyConfig.builder()
      .name(name)
      .comment(comment)
      .encodedKey(publicKey)
      .callerReference(ZonedDateTime.now(ZoneOffset.UTC).toString())
      .build();

    return cloudFrontClient.createPublicKey(
      CreatePublicKeyRequest.builder()
        .publicKeyConfig(config)
        .build()
    );
  }

  @NotNull
  private KeyPair generateKeyPair() throws NoSuchAlgorithmException {
    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
    keyGen.initialize(2048);
    return keyGen.generateKeyPair();
  }

  @NotNull
  private DistributionConfig createDistributionConfig(@NotNull CloudFrontClient cloudFrontClient,
                                                      @NotNull String keyGroupId,
                                                      @NotNull Origin origin,
                                                      @NotNull String comment,
                                                      boolean uploadAllowed) {
    AllowedMethods methods = uploadAllowed ? ALL_METHODS_ALLOWED : ONLY_DOWNLOAD_METHODS_ALLOWED;
    DefaultCacheBehavior cacheBehavior = createDefaultCacheBehavior(cloudFrontClient, keyGroupId, origin.id(), methods);

    final String enchancedComment = comment + (uploadAllowed ? " for Uploads" : " for Downloads");

    return DistributionConfig.builder()
      .defaultCacheBehavior(cacheBehavior)
      .origins(
        Origins.builder()
          .items(origin)
          .quantity(1)
          .build()
      )
      .callerReference(ZonedDateTime.now(ZoneOffset.UTC).toString())
      .comment(enchancedComment)
      .enabled(true)
      .build();
  }

  @NotNull
  private Origin createOrigin(@NotNull String bucketName, @NotNull String bucketRegion, @NotNull String originId, @NotNull String originAccessIdentityId) {

    String oaiId = String.format(S3_CLOUDFRONT_OAI_ID_TEMPLATE, originAccessIdentityId);
    return Origin.builder()
      .s3OriginConfig(
        S3OriginConfig.builder()
          .originAccessIdentity(oaiId)
          .build()
      )
      .domainName(String.format(S3_BUCKET_DOMAIN_PATTERN, bucketName, bucketRegion))
      .id(originId)
      .build();
  }

  @NotNull
  private DefaultCacheBehavior createDefaultCacheBehavior(@NotNull CloudFrontClient cloudFrontClient,
                                                          @NotNull String keyGroupId,
                                                          @NotNull String originId, AllowedMethods allowedMethods) {
    CachePolicy defaultPolicy = getOrCreateCachePolicy(cloudFrontClient);

    TrustedKeyGroups trustedKeyGroups = TrustedKeyGroups.builder()
      .quantity(1)
      .enabled(true)
      .items(keyGroupId)
      .build();
    return DefaultCacheBehavior.builder()
      .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_TO_HTTPS)
      .targetOriginId(originId)
      .allowedMethods(allowedMethods)
      .cachePolicyId(defaultPolicy.id())
      .trustedKeyGroups(trustedKeyGroups)
      .build();
  }

  @NotNull
  private CachePolicy getOrCreateCachePolicy(@NotNull CloudFrontClient cloudFrontClient) {
    final List<CachePolicySummary> existingPolicies = new LinkedList<>();
    CachePolicyList cachePolicyList;
    String marker = null;
    do {
      ListCachePoliciesRequest.Builder requestBuilder = ListCachePoliciesRequest.builder().maxItems("1000").marker(marker);
      cachePolicyList = cloudFrontClient.listCachePolicies(requestBuilder.build()).cachePolicyList();
      existingPolicies.addAll(cachePolicyList.items());
      marker = cachePolicyList.nextMarker();
    } while ( marker != null);

    return existingPolicies
      .stream()
      .map(CachePolicySummary::cachePolicy)
      .filter(IS_GENERATED_POLICY)
      .findAny()
      .orElseGet(() -> createNewPolicy(cloudFrontClient, existingPolicies));
  }

  @NotNull
  private CachePolicy createNewPolicy(@NotNull CloudFrontClient cloudFrontClient, @NotNull List<CachePolicySummary> existingPolicies) {
    CachePolicy defaultPolicy = existingPolicies
      .stream()
      .map(CachePolicySummary::cachePolicy)
      .filter(IS_DEFAULT_POLICY)
      .findAny()
      .orElseThrow(() -> CloudFrontException.builder()
        .message(String.format("Managed Cache policy '%s' not found", S3_CLOUDFRONT_DEFAULT_CACHE_POLICY))
        .build()
      );

    ParametersInCacheKeyAndForwardedToOrigin forwardingParameters = ParametersInCacheKeyAndForwardedToOrigin.builder()
      .queryStringsConfig(
        CachePolicyQueryStringsConfig.builder()
          .queryStringBehavior(CachePolicyQueryStringBehavior.ALL)
          .build()
      )
      .cookiesConfig(
        CachePolicyCookiesConfig.builder()
          .cookieBehavior(CachePolicyCookieBehavior.NONE)
          .build()
      )
      .headersConfig(
        CachePolicyHeadersConfig.builder()
          .headerBehavior(CachePolicyHeaderBehavior.NONE)
          .build()
      )
      .enableAcceptEncodingGzip(true)
      .enableAcceptEncodingBrotli(true)
      .build();

    CreateCachePolicyRequest request = CreateCachePolicyRequest.builder()
      .cachePolicyConfig(
        defaultPolicy.cachePolicyConfig()
          .toBuilder()
          .name(S3_CLOUDFRONT_GENERATED_CACHE_POLICY)
          .comment(BASE_COMMENT)
          .parametersInCacheKeyAndForwardedToOrigin(forwardingParameters)
          .build()
      )
      .build();

    CachePolicy newPolicy = cloudFrontClient.createCachePolicy(request).cachePolicy();

    return newPolicy != null ? newPolicy : defaultPolicy;
  }

  @NotNull
  private String getOriginAccessIdentityId(@NotNull S3Client s3Client, @NotNull CloudFrontClient cloudFrontClient, @NotNull String bucketName) {
    IamPolicy policy = getPolicy(bucketName, s3Client);
    final List<CloudFrontOriginAccessIdentitySummary> existingIdentities = new LinkedList<>();
    CloudFrontOriginAccessIdentityList cloudFrontOriginAccessIdentityList;
    String marker = null;
    do {
      ListCloudFrontOriginAccessIdentitiesRequest.Builder requestBuilder = ListCloudFrontOriginAccessIdentitiesRequest.builder().maxItems("1000").marker(marker);
      cloudFrontOriginAccessIdentityList = cloudFrontClient.listCloudFrontOriginAccessIdentities(requestBuilder.build()).cloudFrontOriginAccessIdentityList();
      existingIdentities.addAll(cloudFrontOriginAccessIdentityList.items());
      marker = cloudFrontOriginAccessIdentityList.nextMarker();
    } while ( marker != null);

    List<String> existingOaiIds = existingIdentities
      .stream()
      .map(CloudFrontOriginAccessIdentitySummary::id)
      .collect(Collectors.toList());
    String oaiId = extractOriginAccessIdentity(policy, existingOaiIds);

    if (oaiId == null) {
      oaiId = getDefaultOriginAccessIdentity(cloudFrontClient, s3Client, bucketName, policy, existingIdentities);
    }
    return oaiId;
  }

  @NotNull
  private String getDefaultOriginAccessIdentity(@NotNull CloudFrontClient cloudFrontClient,
                                                @NotNull S3Client s3Client,
                                                @NotNull String bucketName,
                                                @NotNull IamPolicy policy,
                                                @NotNull List<CloudFrontOriginAccessIdentitySummary> existingIdentities) {
    String oaiId = existingIdentities.stream()
                                     .filter(o -> BASE_COMMENT.equals(o.comment()))
                                     .findFirst()
                                     .map(CloudFrontOriginAccessIdentitySummary::id)
                                     .orElseGet(() -> createOriginAccessIdentity(cloudFrontClient));

    Collection<IamStatement> statements = new LinkedList<>(policy.statements());
    statements.add(generateStatementForOAI(bucketName, oaiId));
    IamPolicy updatedPolicy = policy.toBuilder()
      .statements(statements)
      .build();
    s3Client.putBucketPolicy(builder ->
      builder.bucket(bucketName)
        .policy(updatedPolicy.toJson())
    );
    return oaiId;
  }

  @Nullable
  private String extractOriginAccessIdentity(@NotNull IamPolicy policy, @NotNull List<String> existingIds) {
    String pattern = S3_CLOUDFRONT_OAI_SUBSTRING;

    return policy.statements().stream()
                 .flatMap(s -> s.principals().stream())
                 .filter(p -> IamPrincipalType.AWS.equals(p.type()))
                 .map(IamPrincipal::id)
                 .filter(id -> id.contains(S3_CLOUDFRONT_OAI_SUBSTRING))
                 .map(id -> id.substring(id.indexOf(pattern) + pattern.length()))
                 .filter(existingIds::contains)
                 .findAny().orElse(null);
  }

  @NotNull
  private IamPolicy getPolicy(@NotNull String bucketName, @NotNull S3Client s3Client) {
    GetBucketPolicyResponse bucketPolicy = s3Client.getBucketPolicy(builder -> builder.bucket(bucketName));
    IamPolicy policy;
    if (bucketPolicy.policy() == null) {
      policy = IamPolicy.builder().build();
    } else {
      policy = IamPolicy.fromJson(bucketPolicy.policy());
    }
    return policy;
  }

  @NotNull
  private IamStatement generateStatementForOAI(@NotNull String bucketName, @NotNull String id) {
    return IamStatement.builder()
      .effect(IamEffect.ALLOW)
      .actionIds(Arrays.asList("s3:GetObject", "s3:PutObject",  "s3:DeleteObject"))
      .addPrincipal(b -> b.id(String.format(S3_CLOUDFRONT_PRINCIPAL_TEMPLATE, id)).type(IamPrincipalType.AWS))
      .addResource(IamResource.create(String.format(OAI_RESOURCE_BUCKET_TEMPLATE, bucketName)))
      .build();
  }

  @NotNull
  private String createOriginAccessIdentity(@NotNull CloudFrontClient cloudFrontClient) {
    CloudFrontOriginAccessIdentityConfig config = CloudFrontOriginAccessIdentityConfig.builder()
      .callerReference(BASE_COMMENT)
      .comment(BASE_COMMENT)
      .build();

    CreateCloudFrontOriginAccessIdentityRequest request = CreateCloudFrontOriginAccessIdentityRequest.builder()
      .cloudFrontOriginAccessIdentityConfig(config)
      .build();
    return cloudFrontClient.createCloudFrontOriginAccessIdentity(request)
                           .cloudFrontOriginAccessIdentity()
                           .id();
  }

  static class DistributionCreationResultDTO {
    private final DistributionDTO uploadDistribution;
    private final DistributionDTO downloadDistribution;
    private final String publicKeyId;
    private final String publicKeyName;
    private final String privateKey;

    @Used("xml-serialization")
    public DistributionCreationResultDTO(DistributionDTO uploadDistribution, DistributionDTO downloadDistribution, String publicKeyId, String publicKeyName, String privateKey) {
      this.uploadDistribution = uploadDistribution;
      this.downloadDistribution = downloadDistribution;
      this.publicKeyId = publicKeyId;
      this.publicKeyName = publicKeyName;
      this.privateKey = privateKey;
    }

    public DistributionDTO getUploadDistribution() {
      return uploadDistribution;
    }

    public DistributionDTO getDownloadDistribution() {
      return downloadDistribution;
    }

    public String getPublicKeyId() {
      return publicKeyId;
    }

    public String getPublicKeyName() {
      return publicKeyName;
    }

    public String getPrivateKey() {
      return privateKey;
    }
  }

  static class DistributionDTO {
    private final String id;
    private final String description;

    @Used("xml-serialization")
    public DistributionDTO(String id, String description) {
      this.id = id;
      this.description = description;
    }

    public String getId() {
      return id;
    }

    public String getDescription() {
      return description;
    }
  }
}
