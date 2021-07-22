package jetbrains.buildServer.artifacts.s3;

import java.security.KeyStore;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.SSLContext;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.TimeService;
import jetbrains.buildServer.util.UptodateValue;
import jetbrains.buildServer.util.ssl.SSLContextUtil;
import jetbrains.buildServer.util.ssl.TrustStoreIO;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CachingSocketFactory {
  @NotNull
  private final Map<String, UptodateValue<ConnectionSocketFactory>> myCache = new ConcurrentHashMap<>();
  @NotNull
  private final UptodateValue<Boolean> myEnableCache;

  public CachingSocketFactory(@NotNull final TimeService timeService) {
    myEnableCache = new UptodateValue<>(() -> TeamCityProperties.getBoolean("teamcity.artifacts.socketFactory.cache.enable"), 1000L, timeService, v -> myCache.clear());
  }

  @Nullable
  public ConnectionSocketFactory socketFactory(@Nullable final String certDirectory) {
    if (certDirectory == null) {
      return null;
    }
    if (isCacheEnabled()) {
      return myCache.computeIfAbsent(certDirectory, this::createUptodateConnectionFactory).getValue();
    } else {
      return createFactory(certDirectory);
    }
  }

  @NotNull
  private UptodateValue<ConnectionSocketFactory> createUptodateConnectionFactory(@NotNull final String certDirectory) {
    return new UptodateValue<>(() -> createFactory(certDirectory), TeamCityProperties.getIntervalMilliseconds("teamcity.artifacts.socketFactory.cache.ttl", 60000L));
  }

  @Nullable
  private ConnectionSocketFactory createFactory(@NotNull final String certDirectory) {
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

  @Nullable
  private KeyStore trustStore(@Nullable final String directory) {
    if (directory == null) {
      return null;
    }
    return TrustStoreIO.readTrustStoreFromDirectory(directory);
  }

  private boolean isCacheEnabled() {
    return myEnableCache.getValue();
  }
}
