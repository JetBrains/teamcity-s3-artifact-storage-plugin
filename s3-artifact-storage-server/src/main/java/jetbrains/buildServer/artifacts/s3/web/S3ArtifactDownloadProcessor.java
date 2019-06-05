package jetbrains.buildServer.artifacts.s3.web;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import jetbrains.buildServer.artifacts.ArtifactData;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.artifacts.s3.preSignedUrl.S3PreSignedUrlProvider;
import jetbrains.buildServer.artifacts.s3.util.ParamUtil;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.MainConfigProcessor;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.artifacts.StoredBuildArtifactInfo;
import jetbrains.buildServer.util.amazon.AWSException;
import jetbrains.buildServer.web.openapi.artifacts.ArtifactDownloadProcessor;
import org.apache.commons.io.FilenameUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author vbedrosova
 */
public class S3ArtifactDownloadProcessor implements ArtifactDownloadProcessor, MainConfigProcessor {

  public static final String S3_ELEMENT = "s3";
  public static final String EXTENSION_EXCLUSION_LIST_ELEMENT = "extensionExclusionList";

  private final static Logger LOG = Logger.getInstance(S3ArtifactDownloadProcessor.class.getName());

  private S3PreSignedUrlProvider myPreSignedUrlProvider;
  private final ServerPaths myServerPaths;

  private volatile Set<String> excludedFileExtensions = Collections.emptySet();

  public S3ArtifactDownloadProcessor(@NotNull S3PreSignedUrlProvider preSignedUrlProvider,
                                     @NotNull ServerPaths serverPaths) {
    myPreSignedUrlProvider = preSignedUrlProvider;
    myServerPaths = serverPaths;
  }

  @NotNull
  @Override
  public String getType() {
    return S3Constants.S3_STORAGE_TYPE;
  }

  @Override
  public boolean processDownload(@NotNull StoredBuildArtifactInfo storedBuildArtifactInfo,
                                 @NotNull BuildPromotion buildPromotion,
                                 @NotNull HttpServletRequest httpServletRequest,
                                 @NotNull HttpServletResponse httpServletResponse) throws IOException {
    final ArtifactData artifactData = storedBuildArtifactInfo.getArtifactData();
    if (artifactData == null) throw new IOException("Can not process artifact download request for a folder");

    final Map<String, String> params = S3Util.validateParameters(storedBuildArtifactInfo.getStorageSettings());
    final String pathPrefix = S3Util.getPathPrefix(storedBuildArtifactInfo.getCommonProperties());

    final String bucketName = S3Util.getBucketName(params);
    if (bucketName == null) {
      final String message = "Failed to create pre-signed URL: bucket name is not specified, check S3 storage profile settings";
      LOG.warn(message);
      throw new IOException(message);
    }

    String artifactPath = artifactData.getPath();
    if (excludedFileExtensions.contains(FilenameUtils.getExtension(artifactPath))) {
      // these extensions are excluded from using the the redirect, send the response back with the contents in S3
      final String key = S3Util.getPathPrefix(storedBuildArtifactInfo.getCommonProperties()) + artifactPath;

      try {
        S3ObjectInputStream s3ObjectInputStream = S3Util.withS3Client(
          ParamUtil.putSslValues(myServerPaths, params),
          client -> client.getObject(bucketName, key).getObjectContent()
        );

        long contentLength = stream(s3ObjectInputStream, httpServletResponse.getOutputStream());
        httpServletResponse.setContentLengthLong(contentLength);

      } catch (Throwable t) {
        final AWSException awsException = new AWSException(t);
        final String details = awsException.getDetails();
        if (StringUtil.isNotEmpty(details)) {
          final String message = awsException.getMessage() + details;
          LOG.warn(message);
        }
        throw new IOException(String.format(
          "Failed to get artifact '%s' content in bucket '%s': %s",
          artifactPath, bucketName, awsException.getMessage()
        ), awsException);
      }

    } else {
      // these extensions are not excluded, redirect the client to retrieve it directly from S3
      httpServletResponse.setHeader("Cache-Control", "max-age=" + myPreSignedUrlProvider.getUrlLifetimeSec());
      httpServletResponse.sendRedirect(myPreSignedUrlProvider.getPreSignedUrl(HttpMethod.valueOf(httpServletRequest.getMethod()), bucketName, pathPrefix + artifactData.getPath(), params));
    }
    return true;
  }

  @Override
  public void readFrom(Element rootElement) {
    Element s3Element = rootElement.getChild(S3_ELEMENT);

    if (s3Element != null) {
      String extensionExclusionList = s3Element.getChildText(EXTENSION_EXCLUSION_LIST_ELEMENT);

      if (!StringUtil.isEmpty(extensionExclusionList)) {
        LOG.info(String.format("Updating extensionExclusionList to %s", extensionExclusionList));

        this.excludedFileExtensions = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(extensionExclusionList.split(","))));
      } else {
        LOG.info("extensionExclusionList is empty");
      }
    }
  }

  @Override
  public void writeTo(Element parentElement) {
    Element s3Element = new Element(S3_ELEMENT);
    Element extensionExclusionListElement = new Element(EXTENSION_EXCLUSION_LIST_ELEMENT);
    extensionExclusionListElement.setText(StringUtil.join(excludedFileExtensions, ","));
    s3Element.addContent(extensionExclusionListElement);
    parentElement.addContent(s3Element);
  }

  private static long stream(InputStream input, OutputStream output) throws IOException {
    try (
      ReadableByteChannel inputChannel = Channels.newChannel(input);
      WritableByteChannel outputChannel = Channels.newChannel(output)
    ) {
      ByteBuffer buffer = ByteBuffer.allocateDirect(10240);
      long size = 0;

      while (inputChannel.read(buffer) != -1) {
        buffer.flip();
        size += outputChannel.write(buffer);
        buffer.clear();
      }

      return size;
    }
  }

  Set<String> getExcludedFileExtensions() {
    return excludedFileExtensions;
  }
}
