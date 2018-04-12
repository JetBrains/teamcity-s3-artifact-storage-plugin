package jetbrains.buildServer.artifacts.s3;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.transfer.Transfer;
import jetbrains.buildServer.artifacts.ArtifactListData;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.amazon.AWSClients;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import jetbrains.buildServer.util.ssl.SSLContextUtil;
import jetbrains.buildServer.util.ssl.TrustStoreReader;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.net.URLConnection;
import java.security.KeyStore;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_BUCKET_NAME;
import static jetbrains.buildServer.util.amazon.AWSCommonParams.SSL_CERT_DIRECTORY_PARAM;

/**
 * Created by Nikita.Skvortsov
 * date: 02.08.2016.
 */
public class S3Util {

  private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

  @NotNull
  public static Map<String, String> validateParameters(@NotNull Map<String, String> params, boolean acceptReferences) {
    final Map<String, String> invalids = new HashMap<String, String>();
    if (StringUtil.isEmptyOrSpaces(getBucketName(params))) {
      invalids.put(S3_BUCKET_NAME, "S3 bucket name must not be empty");
    }
    invalids.putAll(AWSCommonParams.validate(params, acceptReferences));
    return invalids;
  }

  @NotNull
  public static Map<String, String> validateParameters(@NotNull Map<String, String> params) throws IllegalArgumentException {
    final Map<String, String> invalids = validateParameters(params, false);
    if (invalids.isEmpty()) return params;
    throw new IllegalArgumentException(joinStrings(invalids.values()));
  }

  @NotNull
  private static String joinStrings(@NotNull Collection<String> strings) {
    if (strings.isEmpty()) return StringUtil.EMPTY;
    final StringBuilder sb = new StringBuilder();
    for (String s : strings) sb.append(s).append("\n");
    return sb.toString();
  }

  @Nullable
  public static String getBucketName(@NotNull Map<String, String> params) {
    return params.get(S3Constants.S3_BUCKET_NAME);
  }

  @Nullable
  public static String getPathPrefix(@NotNull ArtifactListData artifactsInfo) {
    return getPathPrefix(artifactsInfo.getCommonProperties());
  }

  @Nullable
  public static String getPathPrefix(@NotNull Map<String, String> properties) {
    return properties.get(S3Constants.S3_PATH_PREFIX_ATTR);
  }

  public static boolean usePreSignedUrls(@NotNull Map<String, String> properties) {
    return Boolean.parseBoolean(properties.get(S3Constants.S3_USE_PRE_SIGNED_URL_FOR_UPLOAD));
  }

  private static boolean useSignatureVersion4(@NotNull Map<String, String> properties) {
    return Boolean.parseBoolean(properties.get(S3Constants.S3_USE_SIGNATURE_V4));
  }

  @Nullable
  private static KeyStore trustStore(@Nullable final String directory) {
    if (directory == null) {
      return null;
    }
    return TrustStoreReader.readTrustStoreFromDirectory(directory);
  }

  @Nullable
  private static ConnectionSocketFactory socketFactory(@NotNull final Map<String, String> params) {
    final String certDirectory = params.get(SSL_CERT_DIRECTORY_PARAM);
    if (certDirectory == null) {
      return null;
    }
    final KeyStore trustStore = trustStore(certDirectory);
    if (trustStore == null) {
      return null;
    }
    final SSLContext sslContext = SSLContextUtil.createUserSSLContext(trustStore);
    if (sslContext == null) {
      return null;
    }
    return new SSLConnectionSocketFactory(sslContext);
  }

  public static <T, E extends Throwable> T withS3Client(
    @NotNull final Map<String, String> params,
    @NotNull final WithS3<T, E> withClient) throws E {
    return AWSCommonParams.withAWSClients(params, new AWSCommonParams.WithAWSClients<T, E>() {
      @Nullable
      @Override
      public T run(@NotNull AWSClients clients) throws E {
        if (useSignatureVersion4(params)) {
          clients.setS3SignerType("AWSS3V4SignerType");
        }
        patchAWSClientsSsl(clients, params);
        return withClient.run(clients.createS3Client());
      }
    });
  }

  public static <T extends Transfer> Collection<T> withTransferManager(
    @NotNull final Map<String, String> params,
    @NotNull final jetbrains.buildServer.util.amazon.S3Util.WithTransferManager<T> withTransferManager
  ) throws Throwable {
    return AWSCommonParams.withAWSClients(params, new AWSCommonParams.WithAWSClients<Collection<T>, Throwable>() {
      @NotNull
      @Override
      public Collection<T> run(@NotNull AWSClients clients) throws Throwable {
        patchAWSClientsSsl(clients, params);
        return jetbrains.buildServer.util.amazon.S3Util.withTransferManager(clients.createS3Client(), true, withTransferManager);
      }
    });
  }

  private static void patchAWSClientsSsl(@NotNull final AWSClients clients, @NotNull final Map<String, String> params) {
    final ConnectionSocketFactory socketFactory = socketFactory(params);
    if (socketFactory != null) {
      clients.getClientConfiguration().getApacheHttpClientConfig().withSslSocketFactory(socketFactory);
    }
  }

  public static String getContentType(File file) {
    return StringUtil.notEmpty(URLConnection.guessContentTypeFromName(file.getName()), DEFAULT_CONTENT_TYPE);
  }

  public static String normalizeArtifactPath(final String path, final File file) {
    if (StringUtil.isEmpty(path)) {
      return file.getName();
    } else {
      return FileUtil.normalizeRelativePath(String.format("%s/%s", path, file.getName()));
    }
  }

  public interface WithS3<T, E extends Throwable> {
    @Nullable
    T run(@NotNull AmazonS3 client) throws E;
  }
}
