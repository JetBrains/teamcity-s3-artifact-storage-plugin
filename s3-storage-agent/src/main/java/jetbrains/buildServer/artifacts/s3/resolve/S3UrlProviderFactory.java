package jetbrains.buildServer.artifacts.s3.resolve;

import jetbrains.buildServer.artifacts.ArtifactTransportFactory;
import jetbrains.buildServer.artifacts.ArtifactURLProvider;
import jetbrains.buildServer.artifacts.DependencyResolverContext;
import jetbrains.buildServer.artifacts.URLProviderFactoryExtension;
import org.jetbrains.annotations.NotNull;

/**
 * Created by Nikita.Skvortsov
 * date: 31.03.2016.
 */
public class S3UrlProviderFactory implements URLProviderFactoryExtension {

  @NotNull
  private final ArtifactTransportFactory myDefaultTransport;

  public S3UrlProviderFactory(@NotNull ArtifactTransportFactory defaultTransport) {
    myDefaultTransport = defaultTransport;
  }

  @Override
  public ArtifactURLProvider getUrlProvider(DependencyResolverContext dependencyResolverContext) {
    return new S3ArtifactURLProvider(myDefaultTransport.getTransport(dependencyResolverContext),
        myDefaultTransport.getUrlProvider(dependencyResolverContext));
  }
}
