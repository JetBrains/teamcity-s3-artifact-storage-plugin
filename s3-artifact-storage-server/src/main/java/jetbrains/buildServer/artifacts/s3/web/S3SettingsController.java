package jetbrains.buildServer.artifacts.s3.web;

import com.amazonaws.AmazonClientException;
import com.intellij.openapi.diagnostic.Logger;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.controllers.ActionErrors;
import jetbrains.buildServer.controllers.BaseFormXmlController;
import jetbrains.buildServer.controllers.BasePropertiesBean;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.ModelAndView;

public class S3SettingsController extends BaseFormXmlController {

  private final static Logger LOG = Logger.getInstance(S3SettingsController.class.getName());
  private final Map<String, ResourceHandler> myHandlers = new HashMap<>();

  public S3SettingsController(WebControllerManager manager,
                              PluginDescriptor descriptor) {
    final String path = descriptor.getPluginResourcesPath(S3Constants.S3_SETTINGS_PATH + ".html");
    manager.registerController(path, this);
    myHandlers.put("buckets", new BucketsResourceHandler());
    myHandlers.put("bucketLocation", new BucketLocationHandler());
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
    final Map<String, String> parameters = getProperties(request);

    final String resource = request.getParameter("resource");
    if (resource == null) {
      errors.addError("resource", "Invalid request: resource parameter was not set");
    } else {
      final ResourceHandler handler = myHandlers.get(resource);
      if (handler == null) {
        errors.addError("resource","Invalid request: unsupported resource " + resource);
      } else {
        try {
          xmlResponse.addContent(handler.getContent(parameters));
        } catch (Throwable e) {
          final StringBuilder messageBuilder = new StringBuilder(String.format("Failed to process '%s' request: ", resource));
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
    PluginPropertiesUtil.bindPropertiesFromRequest(request, propsBean, true);
    return propsBean.getProperties();
  }
}
