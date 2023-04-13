/*
 * Copyright 2000-2022 JetBrains s.r.o.
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
import jetbrains.buildServer.artifacts.ArtifactData;
import jetbrains.buildServer.artifacts.s3.amazonClient.AmazonS3Provider;
import jetbrains.buildServer.artifacts.s3.util.ParamUtil;
import jetbrains.buildServer.serverSide.BuildsManager;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.artifacts.ArtifactContentProvider;
import jetbrains.buildServer.serverSide.artifacts.StoredBuildArtifactInfo;
import jetbrains.buildServer.serverSide.connections.credentials.ConnectionCredentialsException;
import jetbrains.buildServer.serverSide.impl.artifacts.ExternalBuildArtifactFile;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.amazon.AWSException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * @author vbedrosova
 */
public class S3ArtifactContentProvider implements ArtifactContentProvider {

  private final static Logger LOG = Logger.getInstance(S3ArtifactContentProvider.class.getName());
  private final ServerPaths myServerPaths;
  private final AmazonS3Provider myAmazonS3Provider;
  private final BuildsManager myBuildsManager;

  public S3ArtifactContentProvider(@NotNull ServerPaths serverPaths,
                                   @NotNull AmazonS3Provider amazonS3Provider,
                                   @NotNull BuildsManager buildsManager) {
    myServerPaths = serverPaths;
    myAmazonS3Provider = amazonS3Provider;
    myBuildsManager = buildsManager;
  }

  @NotNull
  @Override
  public String getType() {
    return S3Constants.S3_STORAGE_TYPE;
  }

  @NotNull
  @Override
  public InputStream getContent(@NotNull StoredBuildArtifactInfo storedBuildArtifactInfo) throws IOException {
    final Map<String, String> params;
    final ArtifactData artifactData = storedBuildArtifactInfo.getArtifactData();
    if (artifactData == null) {
      throw new IOException("Invalid artifact data: S3 object path property is not set");
    }

    final String artifactPath = artifactData.getPath();
    try {
      params = S3Util.validateParameters(storedBuildArtifactInfo.getStorageSettings());
    } catch (IllegalArgumentException e) {
      throw new IOException("Failed to get artifact " + artifactPath + " content: Invalid storage settings " + e.getMessage(), e);
    }

    final String bucketName = S3Util.getBucketName(params);
    final String key = S3Util.getPathPrefix(storedBuildArtifactInfo.getCommonProperties()) + artifactPath;

    try {
      //TODO: better approach - we need to slightly change the core to provide some information about build from StoredBuildArtifactInfo
      long buildId = ((ExternalBuildArtifactFile)storedBuildArtifactInfo).getBuildId();
      myBuildsManager.findBuildInstanceById(buildId);
      SBuild build = myBuildsManager.findBuildInstanceById(buildId);
      if (build == null) {
        throw new ConnectionCredentialsException("Cannot find build with id: " + Long.toString(buildId) + " bucket: " + bucketName);
      }

      String projectId = build.getProjectId();
      if (projectId == null) {
        throw new ConnectionCredentialsException("There is no project information in the build : " + Long.toString(buildId) + " bucket: " + bucketName);
      }

      return myAmazonS3Provider.withS3Client(
        ParamUtil.putSslValues(myServerPaths, params),
        projectId,
        client -> client.getObject(bucketName, key).getObjectContent()
      );
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
  }
}
