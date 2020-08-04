/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import java.net.URL;
import java.util.*;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.XmlUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * Created by Evgeniy Koshkin (evgeniy.koshkin@jetbrains.com) on 21.07.17.
 */
public class S3PreSignUrlHelper {
  @NotNull
  private static final Logger LOGGER = Logger.getInstance(S3PreSignUrlHelper.class.getName());
  @NotNull
  private static final String S3_OBJECT_KEY = "s3-object-key";
  @NotNull
  private static final String PRE_SIGN_URL = "pre-sign-url";
  @NotNull
  private static final String S3_PRESIGN_URL_MAP_ENTRY = "s3-presign-url-map-entry";
  @NotNull
  private static final String S3_PRESIGN_URL_MAPPING = "s3-presign-url-mapping";
  @NotNull
  private static final String S3_OBJECT_KEYS = "s3-object-keys";

  @NotNull
  public static Map<String, URL> readPreSignUrlMapping(@NotNull final String data) {
    try {
      final Element rootElement = XmlUtil.from_s(data);
      if (!rootElement.getName().equals(S3_PRESIGN_URL_MAPPING)) return Collections.emptyMap();
      final Map<String, URL> result = new HashMap<>();
      for (Object mapEntryElement : rootElement.getChildren(S3_PRESIGN_URL_MAP_ENTRY)) {
        final Element mapEntryElementCasted = (Element)mapEntryElement;
        final String s3ObjectKey = mapEntryElementCasted.getChild(S3_OBJECT_KEY).getValue();
        final String preSignUrlString = mapEntryElementCasted.getChild(PRE_SIGN_URL).getValue();
        result.put(s3ObjectKey, new URL(preSignUrlString));
      }
      return result;
    } catch (Exception e) {
      LOGGER.warnAndDebugDetails("Got exception while parsing XML", e);
      return Collections.emptyMap();
    }
  }

  @NotNull
  public static String writePreSignUrlMapping(@NotNull Map<String, URL> data) {
    Element rootElement = new Element(S3_PRESIGN_URL_MAPPING);
    for (String s3ObjectKey : data.keySet()) {
      URL preSignUrl = data.get(s3ObjectKey);
      Element mapEntry = new Element(S3_PRESIGN_URL_MAP_ENTRY);
      Element preSignUrlElement = new Element(PRE_SIGN_URL);
      preSignUrlElement.addContent(preSignUrl.toString());
      mapEntry.addContent(preSignUrlElement);
      Element s3ObjectKeyElement = new Element(S3_OBJECT_KEY);
      s3ObjectKeyElement.addContent(s3ObjectKey);
      mapEntry.addContent(s3ObjectKeyElement);
      rootElement.addContent(mapEntry);
    }
    return XmlUtil.toString(rootElement);
  }

  @NotNull
  public static Collection<String> readS3ObjectKeys(@NotNull final String data) {
    try {
      final Element rootElement = XmlUtil.from_s(data);
      if (!rootElement.getName().equals(S3_OBJECT_KEYS)) return Collections.emptyList();
      Collection<String> result = new HashSet<>();
      for (Object element : rootElement.getChildren(S3_OBJECT_KEY)) {
        Element elementCasted = (Element)element;
        result.add(elementCasted.getValue());
      }
      return result;
    } catch (Exception e) {
      LOGGER.warnAndDebugDetails("Got exception while parsing XML", e);
      return Collections.emptyList();
    }
  }

  @NotNull
  public static String writeS3ObjectKeys(@NotNull final Collection<String> s3ObjectKeys) {
    final Element rootElement = new Element(S3_OBJECT_KEYS);
    s3ObjectKeys.stream().filter(StringUtil::isNotEmpty).forEach(s3ObjectKey -> XmlUtil.addTextChild(rootElement, S3_OBJECT_KEY, s3ObjectKey));
    return XmlUtil.toString(rootElement);
  }
}
