package jetbrains.buildServer.artifacts.s3.transfer.storage;

import com.amazonaws.services.s3.AmazonS3;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.artifacts.s3.S3Configuration;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.publish.S3RegularFileUploader;
import jetbrains.buildServer.artifacts.s3.publish.logger.S3Log4jUploadLogger;
import jetbrains.buildServer.artifacts.s3.transfer.model.Build;
import jetbrains.buildServer.java.nio.file.Files;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import org.apache.commons.codec.digest.DigestUtils;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.artifacts.s3.S3Constants.S3_PATH_PREFIX_ATTR;
import static jetbrains.buildServer.artifacts.s3.publish.S3FileUploader.configuration;

public class S3Storage extends AbstractStorage {

  @NotNull
  private final String myFeatureId;
  @NotNull
  private final Map<String, String> myStorageProperties;

  public S3Storage(@NotNull String featureId, @NotNull Map<String, String> storageProperties) {
    myFeatureId = featureId;
    myStorageProperties = storageProperties;
    //TODO get actual secure parameter
    myStorageProperties.put(AWSCommonParams.SECURE_SECRET_ACCESS_KEY_PARAM, "ZMke0fR4oHQjLaF6l0xC+ZqNZHF53yUCKOT7x03K");
  }

  @NotNull
  @Override
  public String getFeatureId() {
    return myFeatureId;
  }

  @NotNull
  @Override
  public String getType() {
    return S3Constants.S3_STORAGE_TYPE;
  }

  @NotNull
  @Override
  public File download(@NotNull String artifact, @NotNull Build Build) {
    throw new UnsupportedOperationException("Download from S3 is not yet supported");
  }

  @Override
  public void upload(@NotNull File artifact, @NotNull Build metadata) {
    S3Configuration s3Configuration = getS3Configuration(metadata);
    S3RegularFileUploader myUploader = new S3RegularFileUploader(s3Configuration, new S3Log4jUploadLogger());
    boolean artifactExists = artifactExists(s3Configuration, artifact.getName());
    boolean artifactConsistent = artifactExists && isConsistent(artifact, metadata);
    if (!artifactConsistent) {
      myUploader.upload(Collections.singletonMap(artifact, ""), () -> "");
    }

    if (!listedAsArtifact(artifact.getName(), metadata)) {
      addToArtifactList(artifact, metadata);
    }
  }

  private boolean isConsistent(File artifact, Build metadata) {
    //TODO check consistency
    return true;
  }

  private boolean artifactExists(S3Configuration configuration, String artifact) {
    return AWSCommonParams.withAWSClients(configuration.getSettingsMap(), clients -> {
      AmazonS3 s3Client = clients.createS3Client();

      return s3Client.doesObjectExist(configuration.getBucketName(), configuration.getPathPrefix() + artifact);
    });
  }

  private S3Configuration getS3Configuration(Build metadata) {
    String pathPrefix = getPathPrefix(metadata);

    S3Configuration s3Configuration = new S3Configuration(configuration(myStorageProperties, myStorageProperties), myStorageProperties);
    s3Configuration.setPathPrefix(pathPrefix);
    return s3Configuration;
  }

  @NotNull
  private String getPathPrefix(Build metadata) {
    final List<String> pathSegments = new ArrayList<>();

    String prefix = myStorageProperties.get(S3Constants.S3_PATH_PREFIX_SETTING);
    if (!StringUtil.isEmptyOrSpaces(prefix)) {
      pathSegments.add(prefix);
    }
    pathSegments.add(metadata.getProjectId());
    pathSegments.add(metadata.getBuildTypeId());
    pathSegments.add(metadata.getBuildId());
    return StringUtil.join("/", pathSegments) + "/";
  }

  @Override
  public void delete(@NotNull File artifact) {
    throw new UnsupportedOperationException("Deletion is not yet supported in S3");
  }

  private String getDigest(File file) throws IOException {
    return DigestUtils.md5Hex(Files.newInputStream(file));
  }

  private String getMultipartDigest(File file, S3Configuration s3Configuration) throws IOException {
    long myChunkSizeInBytes = s3Configuration.getAdvancedConfiguration().getMinimumUploadPartSize();

    final long totalLength = file.length();
    final int nParts = (int)(totalLength % myChunkSizeInBytes == 0 ? totalLength / myChunkSizeInBytes : totalLength / myChunkSizeInBytes + 1);
    String[] myEtags = new String[nParts];
    for (int i = 0; i < nParts; i++) {
      final long contentLength = Math.min(myChunkSizeInBytes, file.length() - myChunkSizeInBytes * i);
      final long start = i * myChunkSizeInBytes;

      try (final FileInputStream fis = new FileInputStream(file)) {
        final long skip = fis.skip(start);
        if (skip != start) {
          throw new IOException("Reader has skipped " + skip + " bytes when supposed to skip " + start);
        }
        BufferedInputStream bis = new BufferedInputStream(fis, (int)contentLength);
        myEtags[i] = DigestUtils.md5Hex(bis);
      }
    }

    return DigestUtils.md5Hex(String.join("", myEtags).getBytes(StandardCharsets.UTF_8)) + "-" + nParts;
  }

  @NotNull
  @Override
  protected Map<String, String> getCommonProperties(@NotNull Build metadata) {
    return CollectionsUtil.asMap(S3_PATH_PREFIX_ATTR, getPathPrefix(metadata));
  }
}
