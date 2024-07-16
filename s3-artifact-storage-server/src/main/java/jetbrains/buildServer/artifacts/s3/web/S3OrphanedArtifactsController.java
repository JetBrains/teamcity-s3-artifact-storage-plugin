package jetbrains.buildServer.artifacts.s3.web;

import com.amazonaws.HttpMethod;
import jetbrains.buildServer.artifacts.s3.orphans.S3OrphanedArtifactsScanner;
import jetbrains.buildServer.controllers.AuthorizationInterceptor;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.controllers.SimpleView;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.Provider;
import java.security.Security;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * REST Controller for {@link S3OrphanedArtifactsScanner}
 * Only works for GET requests and if the flag {@link #SCANNER_ENABLED_FLAG} is enabled
 */
public class S3OrphanedArtifactsController extends BaseController {

  private static final String SCANNER_ENABLED_FLAG = "teamcity.s3.artifacts.scan.enabled";
  private static final String SCANNER_PAUSE_INTERVAL_SECONDS = "teamcity.s3.artifacts.scan.pauseIntervalInSeconds";
  private static final int SCANNER_DEFAULT_PAUSE_INTERVAL_SECONDS = 5;
  public static final String CONTROLLER_PATH = "/artifacts/s3/scan.html";
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
      final ModelAndView error = SimpleView.createTextView("Artifact scanner is not enabled");
      error.setStatus(HttpStatus.FORBIDDEN);
      return error;
    }

    final String projectId = request.getParameter("projectId");
    final boolean scanBuilds = Boolean.parseBoolean(request.getParameter("scanBuilds"));
    final boolean calculateSizes = Boolean.parseBoolean(request.getParameter("calculateSizes"));
    final boolean skipErrors = Boolean.parseBoolean(request.getParameter("skipErrors"));
    final SUser user = SessionUser.getUser(request);
    final int pauseInterval = TeamCityProperties.getInteger(SCANNER_PAUSE_INTERVAL_SECONDS, SCANNER_DEFAULT_PAUSE_INTERVAL_SECONDS);
    if (myScanner.getLastScanTimestamp().isBefore(Instant.now().minus(pauseInterval, ChronoUnit.SECONDS))) {
      final boolean scanStarted = myScanner.tryScanArtifacts(projectId, user, scanBuilds, calculateSizes, skipErrors);
      if (scanStarted) {
        response.getWriter().println("Starting the scan");
        response.setStatus(HttpServletResponse.SC_ACCEPTED);
      } else if (myScanner.isScanning()) {
        final int currentScannedPaths = myScanner.getScannedPathsCount();
        response.getWriter().println("Scan in progress. Scanned: " + currentScannedPaths + " paths");
        response.setStatus(HttpServletResponse.SC_ACCEPTED);
      } else if (myScanner.getLastScanError() != null) {
        final ModelAndView error = SimpleView.createTextView("Scan ended with an error: " + myScanner.getLastScanError());
        error.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        return error;
      }
    } else {
      response.getWriter().println("Last scan finished at " + DateTimeFormatter.ISO_INSTANT.format(myScanner.getLastScanTimestamp()));
      response.getWriter().println("Please wait for " + pauseInterval + " seconds before starting another scan");
      response.setStatus(HttpServletResponse.SC_ACCEPTED);
    }
    return null;
  }

}
