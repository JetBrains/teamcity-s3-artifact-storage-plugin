package jetbrains.buildServer.artifacts.s3.publish;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.artifacts.s3.S3PreSignUrlHelper;
import jetbrains.buildServer.http.HttpUtil;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static jetbrains.buildServer.artifacts.s3.S3Constants.ARTEFACTS_S3_UPLOAD_PRESIGN_URLS_HTML;

/**
 * Created by Evgeniy Koshkin (evgeniy.koshkin@jetbrains.com) on 20.07.17.
 */
class S3PreSignedUrlResolver {
  private static final Logger LOG = Logger.getInstance(S3PreSignedUrlResolver.class.getName());

  private static final String HTTP_AUTH = "/httpAuth";
  private static final String APPLICATION_XML = "application/xml";
  private static final String UTF_8 = "UTF-8";

  @NotNull
  Map<String, URL> resolveUploadUrls(AgentRunningBuild build, @NotNull Collection<String> s3ObjectKeys) throws IOException {
    BuildAgentConfiguration agentConfiguration = build.getAgentConfiguration();
    String targetUrl = agentConfiguration.getServerUrl() + HTTP_AUTH + ARTEFACTS_S3_UPLOAD_PRESIGN_URLS_HTML;
    int connectionTimeout = agentConfiguration.getServerConnectionTimeout();
    UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(build.getAccessUser(), build.getAccessCode());
    HttpClient httpClient = HttpUtil.createHttpClient(connectionTimeout, new URL(targetUrl), credentials);
    PostMethod post = new PostMethod(targetUrl);
    post.setRequestEntity(new StringRequestEntity(S3PreSignUrlHelper.writeS3ObjectKeys(s3ObjectKeys), APPLICATION_XML, UTF_8));
    post.setDoAuthentication(true);
    int responseCode = httpClient.executeMethod(post);
    if(responseCode != 200){
      LOG.debug("Failed resolving S3 presign URLs for build " + build.describe(false) + " . Response code " + responseCode);
      return Collections.emptyMap();
    }
    return S3PreSignUrlHelper.readPreSignUrlMapping(post.getResponseBodyAsString());
  }
}
