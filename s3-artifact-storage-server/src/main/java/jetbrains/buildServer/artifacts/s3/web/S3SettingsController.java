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

package jetbrains.buildServer.artifacts.s3.web;

import com.amazonaws.AmazonClientException;
import com.intellij.openapi.diagnostic.Logger;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.artifacts.s3.*;
import jetbrains.buildServer.artifacts.s3.amazonClient.AmazonS3Provider;
import jetbrains.buildServer.artifacts.s3.exceptions.InvalidSettingsException;
import jetbrains.buildServer.artifacts.s3.util.ParamUtil;
import jetbrains.buildServer.controllers.ActionErrors;
import jetbrains.buildServer.controllers.BaseFormXmlController;
import jetbrains.buildServer.controllers.BasePropertiesBean;
import jetbrains.buildServer.serverSide.IOGuard;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.connections.credentials.ConnectionCredentialsException;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.ModelAndView;

import static jetbrains.buildServer.artifacts.s3.S3Constants.PROJECT_ID_PARAM;


public class S3SettingsController extends BaseFormXmlController {

  private final static Logger LOG = Logger.getInstance(S3SettingsController.class.getName());
  private static final String FAILED_TO_PROCESS_REQUEST_FORMAT = "Failed to process '%s' request: ";
  private final Map<String, S3ClientResourceFetcher> myHandlers = new HashMap<>();
  private final ServerPaths myServerPaths;
  private final ProjectManager myProjectManager;

  public S3SettingsController(@NotNull final WebControllerManager manager,
                              @NotNull final PluginDescriptor descriptor,
                              @NotNull final ServerPaths serverPaths,
                              @NotNull final AmazonS3Provider amazonS3Provider,
                              @NotNull final ProjectManager projectManager) {
    myServerPaths = serverPaths;
    myProjectManager = projectManager;
    final String path = descriptor.getPluginResourcesPath(S3Constants.S3_SETTINGS_PATH + ".html");
    manager.registerController(path, this);
    myHandlers.put("buckets", new ListBucketsResourceFetcher(amazonS3Provider));
    myHandlers.put("bucketLocation", new BucketLocationFetcher(amazonS3Provider));
    myHandlers.put("distributions", new ListCloudFrontDistributionsFetcher(amazonS3Provider));
    myHandlers.put("publicKeys", new ListCloudFrontPublicKeysFetcher(amazonS3Provider));
    myHandlers.put("s3TransferAccelerationAvailability", new CheckS3TransferAccelerationAvailability(amazonS3Provider));
    myHandlers.put("validateCfKeys", new ValidateCloudFrontKeys(amazonS3Provider));
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
          final String projectId = getInternalProjectId(request);
          xmlResponse.addContent(IOGuard.allowNetworkCall(() -> handler.fetchAsElement(parameters, projectId)));
        } catch (ConnectionCredentialsException e) {
          switch (resource) {
            case "buckets":
              errors.addError(resource, "Failed to list buckets: " + e.getMessage());
              break;
            case "distributions":
              errors.addError(resource, "Failed to list distributions: " + e.getMessage());
              break;
            case "publicKeys":
              errors.addError(resource, "Failed to list public keys: " + e.getMessage());
              break;
            case "bucketLocation":
              errors.addError(resource, "Failed to get bucket location: " + e.getMessage());
              break;
            case "validateCfKeys":
              errors.addError(resource, "CloudFront keys validation failed: " + e.getMessage());
              break;
            case "s3TransferAccelerationAvailability":
              errors.addError(resource, "Failed to check S3 Transfer Acceleration availability: " + e.getMessage());
              break;
            default:
              errors.addError(PROJECT_ID_PARAM, "Failed to fetch resource: " + e.getMessage());
          }
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

  @NotNull
  private String getInternalProjectId(@NotNull final HttpServletRequest request) throws ConnectionCredentialsException {
    String externalProjectId = request.getParameter(PROJECT_ID_PARAM);
    if (externalProjectId == null) {
      String errMsg = "Invalid request: projectId parameter was not set";
      throw new ConnectionCredentialsException(errMsg);
    }

    SProject project = myProjectManager.findProjectByExternalId(externalProjectId);
    if (project == null) {
      String errMsg = "Invalid request: cannot find project with ID " + externalProjectId;
      throw new ConnectionCredentialsException(errMsg);
    }

    return project.getProjectId();
  }
}
