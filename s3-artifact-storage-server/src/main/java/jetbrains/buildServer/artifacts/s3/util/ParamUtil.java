

package jetbrains.buildServer.artifacts.s3.util;

import java.util.Map;
import jetbrains.buildServer.artifacts.s3.SSLParamUtil;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.TrustedCertificatesDirectory;
import org.jetbrains.annotations.NotNull;

/**
 * Util class for some work with parameters.
 *
 * @author Mikhail Khorkov
 * @since 2018.1
 */
public class ParamUtil {

  private ParamUtil() {
    throw new IllegalStateException();
  }

  /**
   * Put certificate directory path to param.
   *
   * @param serverPaths server paths
   * @param param       param to store certificate directory
   * @return the new param map.
   */
  @NotNull
  public static Map<String, String> putSslValues(
    @NotNull final ServerPaths serverPaths,
    @NotNull Map<String, String> param
  ) {
    final String certDirectory = TrustedCertificatesDirectory.getCertificateDirectoryForProject(
      serverPaths.getProjectsDir().getPath(), SProject.ROOT_PROJECT_ID);
    return SSLParamUtil.putSslDirectory(param, certDirectory);
  }
}
