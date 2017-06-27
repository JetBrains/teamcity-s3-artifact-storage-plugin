package jetbrains.buildServer.artifacts.s3.web;

import com.amazonaws.services.s3.model.Bucket;
import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.controllers.ActionErrors;
import jetbrains.buildServer.controllers.BaseFormXmlController;
import jetbrains.buildServer.controllers.BasePropertiesBean;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

public class S3SettingsController extends BaseFormXmlController {

  private final static Logger LOG = Logger.getInstance(S3SettingsController.class.getName());

  public S3SettingsController(WebControllerManager manager,
                              PluginDescriptor descriptor) {
    final String path = descriptor.getPluginResourcesPath(S3Constants.S3_SETTINGS_PATH + ".html");
    manager.registerController(path, this);
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

    try {
      final List<Bucket> buckets = AWSCommonParams.withAWSClients(parameters,
        awsClients -> awsClients.createS3Client().listBuckets());
      final Element bucketsElement = new Element("buckets");
      for (Bucket bucket : buckets) {
        final Element bucketElement = new Element("bucket");
        bucketElement.setText(bucket.getName());
        bucketsElement.addContent(bucketElement);
      }
      xmlResponse.addContent(bucketsElement);
    } catch (Throwable e) {
      final String message = String.format("Failed to get list of buckets: %s", e.getMessage());
      LOG.infoAndDebugDetails(message, e);
      errors.addError("buckets", message);
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
