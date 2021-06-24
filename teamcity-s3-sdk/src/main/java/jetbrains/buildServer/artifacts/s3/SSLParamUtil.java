/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

package jetbrains.buildServer.artifacts.s3;

import jetbrains.buildServer.util.amazon.AWSCommonParams;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Utils class to add ssl parameter into params.
 *
 * @author Mikhail Khorkov
 * @since 2018.1
 */
public class SSLParamUtil {

  private SSLParamUtil() {
    throw new IllegalStateException();
  }

  /**
   * Put certificate directory path to param.
   *
   * @param param param to store certificate directory
   * @param certDirectory certificate directory
   * @return changed specified param.
   */
  public static Map<String, String> putSslDirectory(
    @NotNull Map<String, String> param,
    @NotNull final String certDirectory
  ) {
    final HashMap<String, String> result = new HashMap<String, String>(param);
    result.put(AWSCommonParams.SSL_CERT_DIRECTORY_PARAM, certDirectory);
    return result;
  }

}
