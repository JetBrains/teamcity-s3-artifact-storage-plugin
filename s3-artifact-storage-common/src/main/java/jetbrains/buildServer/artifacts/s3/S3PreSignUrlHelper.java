package jetbrains.buildServer.artifacts.s3;

import com.intellij.openapi.util.JDOMUtil;
import jetbrains.buildServer.util.StringUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URL;
import java.util.*;

/**
 * Created by Evgeniy Koshkin (evgeniy.koshkin@jetbrains.com) on 21.07.17.
 */
public class S3PreSignUrlHelper {
  private static final String S3_OBJECT_KEY = "s3-object-key";
  private static final String PRE_SIGN_URL = "pre-sign-url";
  private static final String S3_PRESIGN_URL_MAP_ENTRY = "s3-presign-url-map-entry";
  private static final String S3_PRESIGN_URL_MAPPING = "s3-presign-url-mapping";
  private static final String S3_OBJECT_KEYS = "s3-object-keys";

  @NotNull
  public static Map<String, URL> readPreSignUrlMapping(String data) throws IOException {
    Document document;
    try {
      document = JDOMUtil.loadDocument(data);
    } catch (JDOMException e) {
      return Collections.emptyMap();
    }
    Element rootElement = document.getRootElement();
    if(!rootElement.getName().equals(S3_PRESIGN_URL_MAPPING)) return Collections.emptyMap();
    final Map<String, URL> result = new HashMap<String, URL>();
    for(Object mapEntryElement : rootElement.getChildren(S3_PRESIGN_URL_MAP_ENTRY)){
      Element mapEntryElementCasted = (Element) mapEntryElement;
      String s3ObjectKey = mapEntryElementCasted.getChild(S3_OBJECT_KEY).getValue();
      String preSignUrlString = mapEntryElementCasted.getChild(PRE_SIGN_URL).getValue();
      result.put(s3ObjectKey, new URL(preSignUrlString));
    }
    return result;
  }

  @NotNull
  public static String writePreSignUrlMapping(@NotNull Map<String, URL> data) throws IOException {
    Element rootElement = new Element(S3_PRESIGN_URL_MAPPING);
    for (String s3ObjectKey : data.keySet()){
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
    return JDOMUtil.writeDocument(new Document(rootElement), System.getProperty("line.separator"));
  }

  @NotNull
  public static Collection<String> readS3ObjectKeys(String data) throws IOException {
    Document document;
    try {
      document = JDOMUtil.loadDocument(data);
    } catch (JDOMException e) {
      return Collections.emptyList();
    }
    Element rootElement = document.getRootElement();
    if(!rootElement.getName().equals(S3_OBJECT_KEYS)) return Collections.emptyList();
    Collection<String> result = new HashSet<String>();
    for(Object element : rootElement.getChildren(S3_OBJECT_KEY)) {
      Element elementCasted = (Element) element;
      result.add(elementCasted.getValue());
    }
    return result;
  }

  @NotNull
  public static String writeS3ObjectKeys(@NotNull Collection<String> s3ObjectKeys) throws IOException {
    Element rootElement = new Element(S3_OBJECT_KEYS);
    for (String s3ObjectKey : s3ObjectKeys){
      if(StringUtil.isEmpty(s3ObjectKey)) continue;
      Element xmlElement = new Element(S3_OBJECT_KEY);
      xmlElement.addContent(s3ObjectKey);
      rootElement.addContent(xmlElement);
    }
    return JDOMUtil.writeDocument(new Document(rootElement), System.getProperty("line.separator"));
  }
}
