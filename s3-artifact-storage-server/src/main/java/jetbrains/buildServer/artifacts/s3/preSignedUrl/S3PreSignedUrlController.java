package jetbrains.buildServer.artifacts.s3.preSignedUrl;

import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Created by Evgeniy Koshkin (evgeniy.koshkin@jetbrains.com) on 19.07.17.
 */
public class S3PreSignedUrlController extends BaseController {

  private S3PreSignedUrlProvider myPreSignedUrlProvider;

  public S3PreSignedUrlController(@NotNull WebControllerManager web,
                                  @NotNull S3PreSignedUrlProvider preSignedUrlProvider) {
    myPreSignedUrlProvider = preSignedUrlProvider;
    web.registerController("SOMEPATH", this); //TODO
  }

  @Nullable
  @Override
  protected ModelAndView doHandle(@NotNull HttpServletRequest httpServletRequest, @NotNull HttpServletResponse httpServletResponse) throws Exception {
    if(!isPost(httpServletRequest)){
      httpServletResponse.sendError(HttpServletResponse.SC_BAD_REQUEST);
      return null;
    }

    //TODO: implement

    return new ModelAndView();
  }
}
