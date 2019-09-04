package jetbrains.buildServer.artifacts.s3.preSignedUrl;

import com.amazonaws.HttpMethod;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.StreamUtil;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.BuildAuthUtil;
import jetbrains.buildServer.artifacts.ServerArtifactStorageSettingsProvider;
import jetbrains.buildServer.artifacts.s3.S3PreSignUrlHelper;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.controllers.interceptors.auth.util.AuthorizationHeader;
import jetbrains.buildServer.http.SimpleCredentials;
import jetbrains.buildServer.serverSide.RunningBuildEx;
import jetbrains.buildServer.serverSide.RunningBuildsCollection;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.ModelAndView;

import static jetbrains.buildServer.artifacts.s3.S3Constants.ARTEFACTS_S3_UPLOAD_PRESIGN_URLS_HTML;

/**
 * Created by Evgeniy Koshkin (evgeniy.koshkin@jetbrains.com) on 19.07.17.
 */
public class S3PreSignedUrlController extends BaseController {
  private static final Logger LOG = Logger.getInstance(S3PreSignedUrlController.class.getName());

  private RunningBuildsCollection myRunningBuildsCollection;
  private S3PreSignedUrlProvider myPreSignedUrlProvider;
  private ServerArtifactStorageSettingsProvider myStorageSettingsProvider;

  public S3PreSignedUrlController(@NotNull WebControllerManager web,
                                  @NotNull RunningBuildsCollection runningBuildsCollection,
                                  @NotNull S3PreSignedUrlProvider preSignedUrlProvider,
                                  @NotNull ServerArtifactStorageSettingsProvider storageSettingsProvider) {
    myRunningBuildsCollection = runningBuildsCollection;
    myPreSignedUrlProvider = preSignedUrlProvider;
    myStorageSettingsProvider = storageSettingsProvider;
    web.registerController(ARTEFACTS_S3_UPLOAD_PRESIGN_URLS_HTML, this);
  }

  @Nullable
  @Override
  protected ModelAndView doHandle(@NotNull HttpServletRequest httpServletRequest, @NotNull HttpServletResponse httpServletResponse) throws Exception {
    if(!isPost(httpServletRequest)){
      httpServletResponse.sendError(HttpServletResponse.SC_BAD_REQUEST);
      return null;
    }

    RunningBuildEx runningBuild = getRunningBuild(httpServletRequest);
    if(runningBuild == null){
      httpServletResponse.sendError(HttpServletResponse.SC_BAD_REQUEST);
      LOG.debug("Failed to provide presigned urls for request " + httpServletRequest + ". Can't resolve running build.");
      return null;
    }

    Map<String, String> storageSettings = myStorageSettingsProvider.getStorageSettings(runningBuild);
    try {
      S3Util.validateParameters(storageSettings);
    } catch (IllegalArgumentException ex){
      httpServletResponse.sendError(HttpServletResponse.SC_BAD_REQUEST);
      LOG.debug(
        "Failed to provide presigned urls for request " + httpServletRequest + ". Can't resolve storage settings for running build with id " + LogUtil.describe(runningBuild));
      return null;
    }

    String bucketName = S3Util.getBucketName(storageSettings);
    if(bucketName == null){
      httpServletResponse.sendError(HttpServletResponse.SC_BAD_REQUEST);
      LOG.debug("Failed to provide presigned urls for request " + httpServletRequest + ". Can't resolve target bucket name for build " + LogUtil.describe(runningBuild));
      return null;
    }

    final String text = StreamUtil.readTextFrom(httpServletRequest.getReader());
    final Collection<String> s3ObjectKeys = S3PreSignUrlHelper.readS3ObjectKeys(text);
    if(s3ObjectKeys.isEmpty()){
      httpServletResponse.sendError(HttpServletResponse.SC_BAD_REQUEST);
      LOG.debug("Failed to provide presigned urls for request " + httpServletRequest + ". S3 object keys collection is empty.");
      return null;
    }

    try{
      Map<String, URL> data = new HashMap<>();
      for(String objectKey : s3ObjectKeys){
        data.put(objectKey, new URL(myPreSignedUrlProvider.getPreSignedUrl(HttpMethod.PUT, bucketName, objectKey, storageSettings)));
      }
      httpServletResponse.getWriter().append(S3PreSignUrlHelper.writePreSignUrlMapping(data));
      return null;
    } catch (IOException ex){
      LOG.debug("Failed to resolve presigned upload urls for artifacts of build " + runningBuild.getBuildId(), ex);
      httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return null;
    }
  }

  @Nullable
  private RunningBuildEx getRunningBuild(@NotNull final HttpServletRequest request) {
    AuthorizationHeader header = AuthorizationHeader.getFrom(request);
    if (header != null) {
      SimpleCredentials cre = header.getBasicAuthCredentials();
      if (cre != null) {
        long buildId = BuildAuthUtil.getBuildId(cre.getUsername());
        if (buildId == -1) return null;
        return myRunningBuildsCollection.findRunningBuildById(buildId);
      }
    }

    return null;
  }
}
