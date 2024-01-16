

package jetbrains.buildServer.artifacts.s3.web;

import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import jetbrains.buildServer.controllers.BasePropertiesBean;
import jetbrains.buildServer.serverSide.crypt.RSACipher;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import jetbrains.buildServer.util.amazon.AWSRegions;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Tretyakov
 * Date: 23.06.2017
 */
public class S3StoragePropertiesUtil {
  private static final String PROPERTY_PREFIX = "prop:";
  private static final String ENCRYPTED_PROPERTY_PREFIX = "prop:encrypted:";

  private S3StoragePropertiesUtil() {
  }

  public static void bindPropertiesFromRequest(HttpServletRequest request, BasePropertiesBean bean) {
    final Map<String, String> properties = parsePropertiesFromRequest(request);
    bindProperties(bean, properties);
  }

  @NotNull
  private static Map<String, String> parsePropertiesFromRequest(@NotNull final HttpServletRequest request) {
    final Map<String, String> properties = new HashMap<>();
    for (final String paramName : request.getParameterMap().keySet()) {
      final Property property = parseProperty(paramName, request);
      if (property != null) {
        properties.put(property.getName(), property.getValue());
      }
    }
    processProperties(properties);
    return properties;
  }

  public static void processProperties(@NotNull final Map<String, String> properties) {
    if (useDefaultEnvironment(properties)) {
      setDefaults(properties);
    }
  }

  private static boolean useDefaultEnvironment(@NotNull final Map<String, String> properties) {
    return StringUtil.isEmpty(properties.get(AWSCommonParams.ENVIRONMENT_NAME_PARAM));
  }

  private static void bindProperties(@NotNull final BasePropertiesBean bean, @NotNull final Map<String, String> properties) {
    bean.clearProperties();
    for (final Map.Entry<String, String> property : properties.entrySet()) {
      bean.setProperty(property.getKey(), property.getValue());
    }
  }

  private static void setDefaults(@NotNull final Map<String, String> properties) {
    properties.put(AWSCommonParams.SERVICE_ENDPOINT_PARAM, "");
    properties.put(AWSCommonParams.ENVIRONMENT_NAME_PARAM, "");
    properties.put(AWSCommonParams.REGION_NAME_PARAM, AWSRegions.DEFAULT_REGION);
  }

  @Nullable
  private static Property parseProperty(@NotNull final String paramName, @NotNull final HttpServletRequest request) {
    if (paramName.startsWith(ENCRYPTED_PROPERTY_PREFIX)) {
      return encryptedProperty(paramName, request);
    } else if (paramName.startsWith(PROPERTY_PREFIX)) {
      return plaintextProperty(paramName, request);
    } else {
      return null;
    }
  }

  @NotNull
  private static Property plaintextProperty(@NotNull final String paramName, @NotNull final HttpServletRequest request) {
    final String value = request.getParameter(paramName);
    return Property.of(propertyName(paramName), toUnixLineFeeds(value != null ? value.trim() : null));

  }

  @NotNull
  private static Property encryptedProperty(@NotNull final String paramName, @NotNull final HttpServletRequest request) {
    final String encryptedValue = RSACipher.decryptWebRequestData(request.getParameter(paramName));
    return Property.of(encodedPropertyName(paramName), toUnixLineFeeds(encryptedValue));
  }

  @Contract(value = "null -> null; !null -> !null")
  private static String toUnixLineFeeds(@Nullable final String str) {
    if (str == null) {
      return null;
    }
    return str.replace("\r", "");
  }

  @NotNull
  private static String propertyName(@NotNull final String paramName) {
    return paramName.substring(PROPERTY_PREFIX.length());
  }

  @NotNull
  private static String encodedPropertyName(@NotNull final String paramName) {
    return paramName.substring(ENCRYPTED_PROPERTY_PREFIX.length());
  }

  private static class Property {
    private final String name;
    private final String value;

    Property(@NotNull final String name, @Nullable final String value) {
      if (name.isEmpty()) {
        throw new IllegalArgumentException("Property name cannot be empty");
      }
      this.name = name;
      this.value = value;
    }

    @NotNull
    static Property of(@NotNull final String name, @Nullable final String value) {
      return new Property(name, value);
    }

    @NotNull
    String getName() {
      return name;
    }

    @Nullable
    String getValue() {
      return value;
    }
  }
}
