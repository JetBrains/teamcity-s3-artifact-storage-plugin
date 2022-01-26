/*
 * Copyright 2000-2022 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.artifacts.s3.util;

import jetbrains.buildServer.artifacts.s3.SSLParamUtil;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.TrustedCertificatesDirectory;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

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
