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

package jetbrains.buildServer.artifacts.s3.web;

import com.amazonaws.AmazonClientException;
import com.intellij.openapi.diagnostic.Logger;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.artifacts.s3.BucketLocationFetcher;
import jetbrains.buildServer.artifacts.s3.ListBucketsResourceFetcher;
import jetbrains.buildServer.artifacts.s3.S3ClientResourceFetcher;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.exceptions.InvalidSettingsException;
import jetbrains.buildServer.artifacts.s3.util.ParamUtil;
import jetbrains.buildServer.controllers.ActionErrors;
import jetbrains.buildServer.controllers.BaseFormXmlController;
import jetbrains.buildServer.controllers.BasePropertiesBean;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.ModelAndView;

public class S3SettingsController extends BaseFormXmlController {

  private final static Logger LOG = Logger.getInstance(S3SettingsController.class.getName());
  private static final String FAILED_TO_PROCESS_REQUEST_FORMAT = "Failed to process '%s' request: ";
  private final Map<String, S3ClientResourceFetcher> myHandlers = new HashMap<>();
  private final ServerPaths myServerPaths;

  public S3SettingsController(@NotNull final WebControllerManager manager,
                              @NotNull final PluginDescriptor descriptor,
                              @NotNull final ServerPaths serverPaths) {
    myServerPaths = serverPaths;
    final String path = descriptor.getPluginResourcesPath(S3Constants.S3_SETTINGS_PATH + ".html");
    manager.registerController(path, this);
    myHandlers.put("buckets", new ListBucketsResourceFetcher());
    myHandlers.put("bucketLocation", new BucketLocationFetcher());
  }

  @Override
  protected ModelAndView doGet(@NotNull final HttpServletRequest request,
                               @NotNull final HttpServletResponse response) {
    return null;
  }

  @Override
  protected void doPost(@NotNull final HttpServletRequest request,
                        @NotNull final HttpServletResponse response,
                        @NotNull final Element xmlResponse) {
    final ActionErrors errors = new ActionErrors();
    final Map<String, String> parameters = ParamUtil.putSslValues(myServerPaths, getProperties(request));

    final String resource = request.getParameter("resource");
    if (resource == null) {
      errors.addError("resource", "Invalid request: resource parameter was not set");
    } else {
      final S3ClientResourceFetcher<?> handler = myHandlers.get(resource);
      if (handler == null) {
        errors.addError("resource", "Invalid request: unsupported resource " + resource);
      } else {
        try {
          xmlResponse.addContent(handler.fetchAsElement(parameters));
        } catch (InvalidSettingsException e) {
          final String message = String.format(FAILED_TO_PROCESS_REQUEST_FORMAT, resource);
          if (LOG.isDebugEnabled()) {
            LOG.debug(message, e);
          } else {
            LOG.info(message + e.getMessage());
          }
          e.getInvalids().forEach(errors::addError);
        } catch (IllegalArgumentException e) {
          final String message = String.format(FAILED_TO_PROCESS_REQUEST_FORMAT, resource);
          if (LOG.isDebugEnabled()) {
            LOG.debug(message, e);
          } else {
            LOG.info(message + e.getMessage());
          }
          errors.addError(resource, message);
        } catch (Throwable e) {
          final StringBuilder messageBuilder = new StringBuilder(String.format(FAILED_TO_PROCESS_REQUEST_FORMAT, resource));
          if (e instanceof AmazonClientException && e.getMessage().startsWith("Failed to parse XML document with handler class")) {
            messageBuilder.append(" invalid response. Ensure that the credentials in S3 storage profile are correct.");
          } else {
            messageBuilder.append(e.getMessage());
          }
          final String message = messageBuilder.toString();
          LOG.infoAndDebugDetails(message, e);
          errors.addError(resource, message);
        }
      }
    }

    if (errors.hasErrors()) {
      errors.serialize(xmlResponse);
    }
  }

  private Map<String, String> getProperties(final HttpServletRequest request) {
    final BasePropertiesBean propsBean = new BasePropertiesBean(null);
    S3StoragePropertiesUtil.bindPropertiesFromRequest(request, propsBean);
    return propsBean.getProperties();
  }
}
