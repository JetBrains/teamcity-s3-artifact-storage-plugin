package jetbrains.buildServer.artifacts.s3.web;

import com.amazonaws.AmazonClientException;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.model.Bucket;
import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.controllers.ActionErrors;
import jetbrains.buildServer.controllers.BaseFormXmlController;
import jetbrains.buildServer.controllers.BasePropertiesBean;
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
      xmlResponse.addContent((Content) S3Util.withS3Client(parameters, s3Client -> {
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
      final StringBuilder messageBuilder = new StringBuilder("Failed to get list of buckets: ");
      if (e instanceof AmazonClientException && e.getMessage().startsWith("Failed to parse XML document with handler class")) {
        messageBuilder.append(" invalid response. Ensure that the credentials in S3 storage profile are correct.");
      } else {
        messageBuilder.append(e.getMessage());
      }
      final String message = messageBuilder.toString();
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
