package jetbrains.buildServer.artifacts.s3.preSignedUrl;

import com.amazonaws.HttpMethod;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import jetbrains.buildServer.util.amazon.AWSException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Created by Evgeniy Koshkin (evgeniy.koshkin@jetbrains.com) on 19.07.17.
 */
public class S3PreSignedUrlProvider {
  private static final Logger LOG = Logger.getInstance(S3PreSignedUrlProvider.class.getName());
  public static final int URL_LIFETIME_SEC = TeamCityProperties.getInteger(S3Constants.S3_URL_LIFETIME_SEC, S3Constants.DEFAULT_S3_URL_LIFETIME_SEC);

  private final Cache<String, String> myLinksCache = CacheBuilder.newBuilder()
    .expireAfterWrite(URL_LIFETIME_SEC, TimeUnit.SECONDS)
    .maximumSize(100)
    .build();

  @NotNull
  public String getUploadUrl(@NotNull String bucketName, @NotNull String objectKey, @NotNull Map<String, String> params) throws IOException {
    try {
      return myLinksCache.get(getIdentity(params, objectKey, bucketName), () -> AWSCommonParams.withAWSClients(params, awsClients -> {
        final GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketName, objectKey, HttpMethod.PUT)
          .withExpiration(new Date(System.currentTimeMillis() + URL_LIFETIME_SEC * 1000));
        return awsClients.createS3Client().generatePresignedUrl(request).toString();
      }));
    } catch (ExecutionException e) {
      final AWSException awsException = new AWSException(e.getCause());
      if (StringUtil.isNotEmpty(awsException.getDetails())) {
        LOG.warn(awsException.getDetails());
      }
      final String err = "Failed to create PUT pre-signed URL for [" + objectKey + "] in bucket [" + bucketName + "]";
      LOG.infoAndDebugDetails(err, awsException);
      throw new IOException(err + ": " + awsException.getMessage(), awsException);
    }
  }

  @NotNull
  public String getDownloadUrl(@NotNull String bucketName, @NotNull String objectKey, @NotNull Map<String, String> params) throws IOException {
    try {
      return myLinksCache.get(getIdentity(params, objectKey, bucketName), () -> AWSCommonParams.withAWSClients(params, awsClients -> {
        final GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketName, objectKey, HttpMethod.GET)
          .withExpiration(new Date(System.currentTimeMillis() + URL_LIFETIME_SEC * 1000));
        return awsClients.createS3Client().generatePresignedUrl(request).toString();
      }));
    } catch (ExecutionException e) {
      final AWSException awsException = new AWSException(e.getCause());
      if (StringUtil.isNotEmpty(awsException.getDetails())) {
        LOG.warn(awsException.getDetails());
      }
      final String err = "Failed to create GET pre-signed URL for [" + objectKey + "] in bucket [" + bucketName + "]";
      LOG.infoAndDebugDetails(err, awsException);
      throw new IOException(err + ": " + awsException.getMessage(), awsException);
    }
  }

  @NotNull
  private String getIdentity(@NotNull Map<String, String> params, @NotNull String key, @NotNull String bucket) {
    return String.valueOf(AWSCommonParams.calculateIdentity("", params, bucket, key));
  }
}
