package jetbrains.buildServer.artifacts.s3.web;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.ListBucketsRequest;
import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.controllers.ActionErrors;
import jetbrains.buildServer.controllers.BaseFormXmlController;
import jetbrains.buildServer.controllers.BasePropertiesBean;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jdom.Content;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
      xmlResponse.addContent((Content) AWSCommonParams.withAWSClients(parameters, awsClients -> {
        final AmazonS3Client s3Client = awsClients.createS3Client();
        final Element bucketsElement = new Element("buckets");
        for (Bucket bucket : s3Client.listBuckets()) {
          final Element bucketElement = new Element("bucket");
          final String bucketName = bucket.getName();
          final String location = s3Client.getBucketLocation(bucketName);
          final String regionName = getRegionName(location);
          bucketElement.setAttribute("location", regionName);
          bucketElement.setText(bucketName);
          bucketsElement.addContent(bucketElement);
        }
        return bucketsElement;
      }));
    } catch (Throwable e) {
      final String message = String.format("Failed to get list of buckets: %s", e.getMessage());
      LOG.infoAndDebugDetails(message, e);
      errors.addError("buckets", message);
    }

    if (errors.hasErrors()) {
      errors.serialize(xmlResponse);
    }
  }

  private static String getRegionName(String location) {
    final Region region = RegionUtils.getRegion(location);
    if (region == null && location.equals("US")) {
      return Regions.US_EAST_1.getName();
    }

    return region != null ? region.getName() : location;
  }

  private Map<String, String> getProperties(final HttpServletRequest request) {
    final BasePropertiesBean propsBean = new BasePropertiesBean(null);
    PluginPropertiesUtil.bindPropertiesFromRequest(request, propsBean, true);
    return propsBean.getProperties();
  }
}
