package jetbrains.buildServer.artifacts.s3.publish;

import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.artifacts.s3.S3PreSignUrlHelper;
import jetbrains.buildServer.http.HttpUtil;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.jetbrains.annotations.NotNull;

import java.net.URL;
import java.util.Collection;
import java.util.Map;

import static jetbrains.buildServer.artifacts.s3.S3Constants.ARTEFACTS_S3_UPLOAD_PRESIGN_URLS_HTML;

/**
 * Created by Evgeniy Koshkin (evgeniy.koshkin@jetbrains.com) on 20.07.17.
 */
class S3PreSignedUrlResolver {
  private static final String HTTP_AUTH = "/httpAuth";
  private static final String APPLICATION_XML = "application/xml";
  private static final String UTF_8 = "UTF-8";

  @NotNull
  Map<String, URL> resolveUploadUrls(AgentRunningBuild build, @NotNull Collection<String> s3ObjectKeys) {
    try {
      BuildAgentConfiguration agentConfiguration = build.getAgentConfiguration();
      URL url = new URL(agentConfiguration.getServerUrl() + HTTP_AUTH + ARTEFACTS_S3_UPLOAD_PRESIGN_URLS_HTML);
      int connectionTimeout = agentConfiguration.getServerConnectionTimeout();
      HttpClient httpClient = HttpUtil.createHttpClient(connectionTimeout, url, new UsernamePasswordCredentials(build.getAccessUser(), build.getAccessCode()));
      PostMethod post = new PostMethod();
      post.setRequestEntity(new StringRequestEntity(S3PreSignUrlHelper.writeS3ObjectKeys(s3ObjectKeys), APPLICATION_XML, UTF_8));
      int responseCode = httpClient.executeMethod(post);
      if(responseCode != 200){
        //TODO: handle
      }
      return S3PreSignUrlHelper.readPreSignUrls(post.getResponseBodyAsString());
    } catch (Throwable ex){
      //TODO: handle
    }
  }
}
