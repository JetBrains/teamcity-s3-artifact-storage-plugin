package jetbrains.buildServer.artifacts.s3.download;

import org.jetbrains.annotations.NotNull;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public final class S3MockContainer extends GenericContainer<S3MockContainer> {
  private static final int S3MOCK_DEFAULT_HTTP_PORT = 9090;
  private static final int S3MOCK_DEFAULT_HTTPS_PORT = 9191;
  private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("adobe/s3mock");

  public S3MockContainer(@NotNull String tag) {
    this(DEFAULT_IMAGE_NAME.withTag(tag));
  }

  public S3MockContainer(@NotNull DockerImageName dockerImageName) {
    super(dockerImageName);
    dockerImageName.assertCompatibleWith(DEFAULT_IMAGE_NAME);
    addExposedPort(S3MOCK_DEFAULT_HTTP_PORT);
    addExposedPort(S3MOCK_DEFAULT_HTTPS_PORT);
    waitingFor(Wait.forHttp("/favicon.ico").forPort(S3MOCK_DEFAULT_HTTP_PORT).withMethod("GET").forStatusCode(200));
  }

  public S3MockContainer withRetainFilesOnExit(boolean retainFilesOnExit) {
    addEnv("retainFilesOnExit", String.valueOf(retainFilesOnExit));
    return self();
  }

  public S3MockContainer withInitialBuckets(String initialBuckets) {
    addEnv("initialBuckets", initialBuckets);
    return self();
  }

  public String getHttpEndpoint() {
    return String.format("http://%s:%d", getHost(), getHttpServerPort());
  }

  public String getHttpsEndpoint() {
    return String.format("https://%s:%d", getHost(), getHttpsServerPort());
  }

  public Integer getHttpServerPort() {
    return getMappedPort(S3MOCK_DEFAULT_HTTP_PORT);
  }

  public Integer getHttpsServerPort() {
    return getMappedPort(S3MOCK_DEFAULT_HTTPS_PORT);
  }
}
