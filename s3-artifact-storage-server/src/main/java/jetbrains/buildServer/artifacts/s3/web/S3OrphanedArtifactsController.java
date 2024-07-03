package jetbrains.buildServer.artifacts.s3.web;

import com.amazonaws.HttpMethod;
import com.fasterxml.jackson.databind.ObjectMapper;
import jetbrains.buildServer.artifacts.s3.orphans.OrphanedArtifacts;
import jetbrains.buildServer.artifacts.s3.orphans.S3OrphanedArtifactsScanner;
import jetbrains.buildServer.controllers.AuthorizationInterceptor;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import jetbrains.buildServer.web.util.SessionUser;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.Provider;
import java.security.Security;

/**
 * REST Controller for {@link S3OrphanedArtifactsScanner}
 * Only works for GET requests and if the flag {@link #SCANNER_ENABLED_FLAG} is enabled
 */
public class S3OrphanedArtifactsController extends BaseController {

  private static final String SCANNER_ENABLED_FLAG = "teamcity.s3.artifacts.scan.enabled";
  public static final String CONTROLLER_PATH = "/artefacts/s3/scan.html";
  @NotNull
  private final S3OrphanedArtifactsScanner myScanner;

  public S3OrphanedArtifactsController(
    @NotNull final WebControllerManager controllerManager,
    @NotNull final SBuildServer server,
    @NotNull final AuthorizationInterceptor authInterceptor,
    @NotNull final S3OrphanedArtifactsScanner scanner) {
    super(server);
    this.myScanner = scanner;

    controllerManager.registerController(CONTROLLER_PATH, this);

    authInterceptor.addPathBasedPermissionsChecker(CONTROLLER_PATH, (holder, request) -> {
      String projectId = request.getParameter("projectId");
      if (projectId == null) {
        projectId = SProject.ROOT_PROJECT_ID;
      }
      final boolean hasAccess = holder.isPermissionGrantedForProject(projectId, Permission.EDIT_PROJECT);
      if (!hasAccess) {
        throw new AccessDeniedException(holder, "Authorised user lacks permissions for project " + projectId);
      }
    });

    Provider provider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
    if (provider == null) {
      Security.addProvider(new BouncyCastleProvider());
    }
  }

  @Nullable
  @Override
  protected ModelAndView doHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) throws Exception {
    if (!HttpMethod.GET.name().equals(request.getMethod())) {
      response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
      return null;
    }

    if (!TeamCityProperties.getBoolean(SCANNER_ENABLED_FLAG)) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND, "Service is disabled");
      return null;
    }

    final String projectId = request.getParameter("projectId");
    final boolean scanBuilds = Boolean.parseBoolean(request.getParameter("scanBuilds"));
    final boolean calculateSizes = Boolean.parseBoolean(request.getParameter("calculateSizes"));
    final boolean skipErrors = Boolean.parseBoolean(request.getParameter("skipErrors"));
    final SUser user = SessionUser.getUser(request);

    final OrphanedArtifacts orphanedArtifacts = myScanner.scanArtifacts(projectId, user, scanBuilds, calculateSizes);
    if (orphanedArtifacts != null) {
      final ObjectMapper objectMapper = new ObjectMapper();
      if (skipErrors) {
        response.getWriter().println(objectMapper.writeValueAsString(orphanedArtifacts.getOrphanedPaths()));
      } else {
        response.getWriter().println(objectMapper.writeValueAsString(orphanedArtifacts));
      }
    }

    return null;
  }


}
