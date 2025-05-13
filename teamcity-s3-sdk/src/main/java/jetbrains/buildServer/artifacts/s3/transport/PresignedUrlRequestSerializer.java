

package jetbrains.buildServer.artifacts.s3.transport;

import com.intellij.openapi.diagnostic.Logger;
import java.util.*;
import java.util.stream.Collectors;
import jetbrains.buildServer.util.XmlUtil;
import org.jdom.Attribute;
import org.jdom.Content;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by Evgeniy Koshkin (evgeniy.koshkin@jetbrains.com) on 21.07.17.
 */
public class PresignedUrlRequestSerializer {
  @NotNull
  public static final String UPLOAD_SUCCESSFUL = "s3-multipart-upload-successful";
  @NotNull
  public static final String ETAGS = "s3-part-etags";
  @NotNull
  public static final String FINISH_UPLOAD = "s3-multipart-upload-completed";
  @NotNull
  public static final String OBJECT_KEY = "s3-object-key";
  @NotNull
  private static final Logger LOGGER = Logger.getInstance(PresignedUrlRequestSerializer.class.getName());
  @NotNull
  private static final String PRE_SIGN_V2 = "pre-sign-v2";
  @NotNull
  private static final String OBJECT_KEYS = "s3-object-keys";
  @NotNull
  private static final String PRE_SIGN_URL = "pre-sign-url";
  @NotNull
  private static final String PRE_SIGN_URL_UPLOAD_ID = "s3-multipart-upload-request-id";
  @NotNull
  private static final String PRE_SIGN_URL_PART_NUMBER = "s3-multipart-upload-part-number";
  @NotNull
  private static final String PRE_SIGN_URLS_FOR_FILE_PARTS = "s3-multipart-pre-sign-urls-for-file-parts";
  @NotNull
  private static final String PRE_SIGN_URL_MAP_ENTRY = "s3-presign-url-map-entry";
  @NotNull
  private static final String PRESIGN_URL_MAPPING = "s3-presign-url-mapping";
  @NotNull
  private static final String NUMBER_OF_PARTS = "s3-number-of-parts";
  @NotNull
  private static final String DIGEST = "s3-file-digest";
  @NotNull
  private static final String UPLOAD_ID = "s3-file-upload-id";
  @NotNull
  private static final String CUSTOM_TTL = "s3-custom-ttl";
  @NotNull
  private static final String HTTP_METHOD = "s3-http-method";
  @NotNull
  public static final String S3_UPLOAD_CONTENT_TYPE = "s3-upload-content-type";

  @SuppressWarnings("unchecked")
  @NotNull
  public static PresignedUrlListResponseDto deserializeResponseV2(@NotNull final String response) {
    try {
      final Element rootElement = XmlUtil.from_s(response);
      if (!rootElement.getName().equals(PRESIGN_URL_MAPPING)) {
        throw new IllegalArgumentException("Root element is not " + PRESIGN_URL_MAPPING);
      }
      final List<PresignedUrlDto> responseList = new ArrayList<>();
      for (final Element mapEntryElement : (List<Element>)rootElement.getChildren(PRE_SIGN_URL_MAP_ENTRY)) {
        final String s3ObjectKey = mapEntryElement.getChild(OBJECT_KEY).getValue();
        final List<PresignedUrlPartDto> filePartsPresignedUrls = ((List<Element>)mapEntryElement.getChild(PRE_SIGN_URLS_FOR_FILE_PARTS)
                                                                                                .getChildren(PRE_SIGN_URL))
          .stream()
          .map(PresignedUrlRequestSerializer::fromElement)
          .collect(Collectors.toList());

        final String uploadId = mapEntryElement.getChild(PRE_SIGN_URL_UPLOAD_ID).getValue();
        responseList.add(PresignedUrlDto.multiPart(s3ObjectKey, uploadId, filePartsPresignedUrls));
      }
      return PresignedUrlListResponseDto.createV2(responseList);
    } catch (Exception e) {
      LOGGER.warnAndDebugDetails("Got exception while parsing XML", e);
      throw new IllegalArgumentException("Invalid XML: " + e.getMessage(), e);
    }
  }

  @NotNull
  private static PresignedUrlPartDto fromElement(@NotNull final Element el) {
    @Nullable final String partNumberAttr = el.getAttributeValue(PRE_SIGN_URL_PART_NUMBER);
    return new PresignedUrlPartDto(el.getValue(), partNumberAttr != null ? Integer.parseInt(partNumberAttr) : 1);
  }

  @SuppressWarnings("unchecked")
  @NotNull
  public static PresignedUrlListResponseDto deserializeResponseV1(@NotNull final String data) {
    final Element document = XmlUtil.from_s(data);
    if (!document.getName().equals(PRESIGN_URL_MAPPING)) {
      throw new IllegalArgumentException("XML does not contain required element " + PRESIGN_URL_MAPPING);
    }
    return PresignedUrlListResponseDto.createV1((List<PresignedUrlDto>)document.getChildren(PRE_SIGN_URL_MAP_ENTRY).stream().map(child -> {
      final Element el = (Element)child;
      return PresignedUrlDto.singlePart(el.getChild(OBJECT_KEY).getValue(), el.getChild(PRE_SIGN_URL).getValue());
    }).collect(Collectors.toList()));
  }

  @NotNull
  public static String serializeResponseV1(@NotNull PresignedUrlListResponseDto response) {
    final Element document = new Element(PRESIGN_URL_MAPPING);
    for (PresignedUrlDto presignedUrl : response.getPresignedUrls()) {
      final Element presignedUrlEl = new Element(PRE_SIGN_URL_MAP_ENTRY);
      presignedUrl.getPresignedUrlParts()
                  .forEach(url -> XmlUtil.addTextChild(presignedUrlEl, PRE_SIGN_URL, url.getUrl()).setAttribute(PRE_SIGN_URL_PART_NUMBER, String.valueOf(url.getPartNumber())));
      XmlUtil.addTextChild(presignedUrlEl, OBJECT_KEY, presignedUrl.getObjectKey());
      document.addContent(presignedUrlEl);
    }
    return XmlUtil.toString(document);
  }

  @NotNull
  public static String serializeResponseV2(@NotNull final PresignedUrlListResponseDto responseList) {
    final Element document = new Element(PRESIGN_URL_MAPPING);
    for (PresignedUrlDto presignedUrl : responseList.getPresignedUrls()) {
      final Element mapEntry = new Element(PRE_SIGN_URL_MAP_ENTRY);
      final Element uploadIdElement = new Element(PRE_SIGN_URL_UPLOAD_ID);
      uploadIdElement.addContent(presignedUrl.getUploadId());
      mapEntry.addContent((Content)uploadIdElement);

      final Element preSignUrlsElement = new Element(PRE_SIGN_URLS_FOR_FILE_PARTS);
      presignedUrl.getPresignedUrlParts()
                  .forEach(url -> XmlUtil.addTextChild(preSignUrlsElement, PRE_SIGN_URL, url.getUrl()).setAttribute(PRE_SIGN_URL_PART_NUMBER, String.valueOf(url.getPartNumber())));
      mapEntry.addContent((Content)preSignUrlsElement);
      Element s3ObjectKeyElement = new Element(OBJECT_KEY);
      s3ObjectKeyElement.addContent(presignedUrl.getObjectKey());
      mapEntry.addContent((Content)s3ObjectKeyElement);
      document.addContent((Content)mapEntry);
    }
    return XmlUtil.toString(document);
  }

  @NotNull
  public static String serializeRequestV1(@NotNull final Collection<String> s3ObjectKeys) {
    return serializeRequest(PresignedUrlListRequestDto.forObjectKeys(s3ObjectKeys), false);
  }

  @NotNull
  public static String serializeRequestV2(@NotNull final PresignedUrlListRequestDto request) {
    return serializeRequest(request, true);
  }

  @NotNull
  private static String serializeRequest(@NotNull final PresignedUrlListRequestDto request, final boolean isVersion2) {
    final Element document = new Element(OBJECT_KEYS);
    if (isVersion2) {
      document.setAttribute(PRE_SIGN_V2, "true");
    }
    if (request.getCustomTtl() != null) {
      document.setAttribute(CUSTOM_TTL, String.valueOf(request.getCustomTtl()));
    }
    if (request.getMultipartContentType() != null) {
      document.setAttribute(S3_UPLOAD_CONTENT_TYPE, request.getMultipartContentType());
    }
    request.getPresignedUrlRequests().stream().filter(Objects::nonNull).forEach(s3ObjectKey -> {
      final Element element = XmlUtil.addTextChild(document, OBJECT_KEY, s3ObjectKey.getObjectKey());
      element.setAttribute(NUMBER_OF_PARTS, String.valueOf(s3ObjectKey.getDigests() != null ? s3ObjectKey.getDigests().size() : s3ObjectKey.getNumberOfParts()));
      if (s3ObjectKey.getDigests() != null) {
        for (String digest : s3ObjectKey.getDigests()) {
          XmlUtil.addTextChild(element, DIGEST, digest);
        }
      }
      if (s3ObjectKey.getUploadId() != null) {
        element.setAttribute(UPLOAD_ID, s3ObjectKey.getUploadId());
      }
    });
    return XmlUtil.toString(document);
  }

  @NotNull
  public static PresignedUrlListRequestDto deserializeRequest(@NotNull final String data) {
    try {
      final Element document = XmlUtil.from_s(data);
      if (!document.getName().equals(OBJECT_KEYS)) {
        throw new IllegalArgumentException("Expected document name to be '" + OBJECT_KEYS + "' but got '" + document.getName() + "'");
      }
      final Collection<PresignedUrlRequestDto> result = new HashSet<>();
      final boolean isVersion2 = document.getAttribute(PRE_SIGN_V2) != null;

      final Attribute customTtlAttribute = document.getAttribute(CUSTOM_TTL);
      final Long customTtl = customTtlAttribute != null ? customTtlAttribute.getLongValue() : null;
      Attribute contentTypeAttribute = document.getAttribute(S3_UPLOAD_CONTENT_TYPE);
      String contentType = contentTypeAttribute != null ? contentTypeAttribute.getValue() : null;

      for (Object child : document.getChildren(OBJECT_KEY)) {
        final Element objectKeyEl = (Element)child;
        final Attribute nPartsAttr = objectKeyEl.getAttribute(NUMBER_OF_PARTS);
        final Attribute httpMethodAttr = objectKeyEl.getAttribute(HTTP_METHOD);

        final int numberOfParts = nPartsAttr != null ? nPartsAttr.getIntValue() : 1;
        final String httpMethod = httpMethodAttr != null ? httpMethodAttr.getValue() : null;

        final String text = objectKeyEl.getText().trim();
        if (isVersion2) {
          final Iterator iterator = objectKeyEl.getChildren(DIGEST).iterator();
          final List<String> digests = new ArrayList<>();
          while (iterator.hasNext()) {
            final Element element = (Element)iterator.next();
            digests.add(element.getValue());
          }

          final Attribute uploadIdAttr = objectKeyEl.getAttribute(UPLOAD_ID);
          final String uploadId = uploadIdAttr != null ? uploadIdAttr.getValue() : null;

          if (!digests.isEmpty()) {
            result.add(PresignedUrlRequestDto.from(text, uploadId, digests));
          } else {
            result.add(PresignedUrlRequestDto.from(text, numberOfParts, httpMethod));
          }
        } else {
          result.add(PresignedUrlRequestDto.from(text, numberOfParts, httpMethod));
        }
      }
      return new PresignedUrlListRequestDto(result, isVersion2, customTtl, contentType);
    } catch (Exception e) {
      LOGGER.warnAndDebugDetails("Got exception while parsing XML", e);
      throw new IllegalArgumentException("Request is not a valid XML");
    }
  }
}
