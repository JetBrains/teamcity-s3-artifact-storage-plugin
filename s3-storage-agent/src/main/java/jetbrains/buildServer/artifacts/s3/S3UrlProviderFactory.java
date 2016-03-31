package jetbrains.buildServer.artifacts.s3;

import jetbrains.buildServer.artifacts.ArtifactURLProvider;
import jetbrains.buildServer.artifacts.DependencyResolverContext;
import jetbrains.buildServer.artifacts.URLProviderFactoryExtension;
import jetbrains.buildServer.http.HttpUtil;
import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;

/**
 * Created by Nikita.Skvortsov
 * date: 31.03.2016.
 */
public class S3UrlProviderFactory implements URLProviderFactoryExtension {


  @Override
  public ArtifactURLProvider getUrlProvider(DependencyResolverContext dependencyResolverContext) {
    return new S3ArtifactURLProvider(createHttpClient(dependencyResolverContext));
  }


  public HttpClient createHttpClient(final DependencyResolverContext context) {
    HttpClient client = HttpUtil.createHttpClient(context.getConnectionTimeout());
    client.getParams().setAuthenticationPreemptive(true);
    Credentials defaultcreds = new UsernamePasswordCredentials(context.getUsername(), context.getPassword());
    client.getState().setCredentials(new AuthScope(AuthScope.ANY_HOST,
            AuthScope.ANY_PORT,
            AuthScope.ANY_REALM),
        defaultcreds);
    return client;
  }
}
