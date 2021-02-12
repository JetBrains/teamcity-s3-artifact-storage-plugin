/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

package jetbrains.buildServer.artifacts.s3.preSignedUrl;

import com.amazonaws.SdkBaseException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.StreamUtil;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.BuildAuthUtil;
import jetbrains.buildServer.artifacts.ServerArtifactStorageSettingsProvider;
import jetbrains.buildServer.artifacts.s3.InvalidSettingsException;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.artifacts.s3.transport.*;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.controllers.interceptors.auth.util.AuthorizationHeader;
import jetbrains.buildServer.http.SimpleCredentials;
import jetbrains.buildServer.serverSide.RunningBuildEx;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.serverSide.impl.RunningBuildsManagerEx;
import jetbrains.buildServer.util.ExceptionUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.servlet.ModelAndView;

import static jetbrains.buildServer.artifacts.s3.S3Constants.ARTEFACTS_S3_UPLOAD_PRESIGN_URLS_HTML;
import static jetbrains.buildServer.artifacts.s3.transport.PresignedUrlRequestSerializer.*;

/**
 * Created by Evgeniy Koshkin (evgeniy.koshkin@jetbrains.com) on 19.07.17.
 */
public class S3PreSignedUrlController extends BaseController {
  @NotNull
  private static final Logger LOG = Logger.getInstance(S3PreSignedUrlController.class.getName());
  @NotNull
  private final RunningBuildsManagerEx myRunningBuildsManager;
  @NotNull
  private final S3PreSignedManager myPreSignedManager;
  @NotNull
  private final ServerArtifactStorageSettingsProvider myStorageSettingsProvider;

  public S3PreSignedUrlController(@NotNull WebControllerManager web,
                                  @NotNull RunningBuildsManagerEx runningBuildsManager,
                                  @NotNull S3PreSignedManager preSignedManager,
                                  @NotNull ServerArtifactStorageSettingsProvider storageSettingsProvider) {
    myRunningBuildsManager = runningBuildsManager;
    myPreSignedManager = preSignedManager;
    myStorageSettingsProvider = storageSettingsProvider;
    web.registerController(ARTEFACTS_S3_UPLOAD_PRESIGN_URLS_HTML, this);
  }

  @SuppressWarnings("MagicConstant")
  @Nullable
  @Override
  protected ModelAndView doHandle(@NotNull HttpServletRequest httpServletRequest, @NotNull HttpServletResponse httpServletResponse) throws Exception {
    try {
      final Pair<RequestType, S3PreSignedManager.S3Settings> request = parseRequest(httpServletRequest);

      if (request.getFirst() == RequestType.FINISH_MULTIPART_UPLOAD) {
        finishMultipartUpload(httpServletRequest, request.getSecond());
      } else {
        final String response = providePresignedUrls(httpServletRequest, request.getSecond());
        httpServletResponse.getWriter().append(response);
      }
      httpServletResponse.setStatus(HttpServletResponse.SC_OK);
    } catch (HttpServerErrorException e) {
      httpServletResponse.sendError(e.getStatusCode().value(), e.getMessage());
    } catch (SdkBaseException | IOException | IllegalArgumentException e) {
      httpServletResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
    } catch (Exception e) {
      final SdkBaseException sdkException = ExceptionUtil.getCause(e, SdkBaseException.class);
      if (sdkException != null) {
        httpServletResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, sdkException.getMessage());
      } else {
        httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
      }
    }
    return null;
  }

  @NotNull
  private Pair<RequestType, S3PreSignedManager.S3Settings> parseRequest(@NotNull final HttpServletRequest request) {
    if (!isPost(request)) {
      throw new HttpServerErrorException(HttpStatus.METHOD_NOT_ALLOWED, request.getMethod() + " not allowed");
    }

    final RunningBuildEx runningBuild = getRunningBuild(request);
    if (runningBuild == null) {
      LOG.debug("Failed to provide presigned urls for request " + request + ". Can't resolve running build.");
      throw new HttpServerErrorException(HttpStatus.BAD_REQUEST, "build is missing in request");
    }

    final Map<String, String> storageSettings = myStorageSettingsProvider.getStorageSettings(runningBuild);
    try {
      S3Util.validateParameters(storageSettings);
    } catch (IllegalArgumentException ex) {
      LOG.debug(
        "Failed to provide presigned urls for request " + request + ". Can't resolve storage settings for running build with id " + LogUtil.describe(runningBuild));
      throw ex;
    } catch (InvalidSettingsException ex) {
      LOG.infoAndDebugDetails(() -> "Failed to provide presigned urls, artifact storage settings are invalid " + ex.getMessage() + ". " + LogUtil.describe(runningBuild), ex);
      throw new HttpServerErrorException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
    return Pair.create(RequestType.fromRequest(request), myPreSignedManager.settings(storageSettings));
  }

  @Nullable
  private String providePresignedUrls(@NotNull final HttpServletRequest httpServletRequest,
                                      @NotNull final S3PreSignedManager.S3Settings settings) throws Exception {
    final PresignedUrlListRequestDto request = PresignedUrlRequestSerializer.deserializeRequest(StreamUtil.readTextFrom(httpServletRequest.getReader()));
    return request.isVersion2
           ? presignedUrlsV2(request, settings)
           : presignedUrlsV1(request, settings);
  }

  @NotNull
  private String presignedUrlsV2(@NotNull final PresignedUrlListRequestDto requestList,
                                 @NotNull final S3PreSignedManager.S3Settings settings) {
    final List<PresignedUrlDto> responses = requestList.presignedUrlRequests.stream().map(request -> {
      try {
        if (request.numberOfParts > 1) {
          final String uploadId = myPreSignedManager.startMultipartUpload(request.objectKey, settings);
          final List<PresignedUrlPartDto> presignedUrls = IntStream.rangeClosed(1, request.numberOfParts).mapToObj(partNumber -> {
            try {
              return new PresignedUrlPartDto(myPreSignedManager.generateUploadUrlForPart(request.objectKey, partNumber, uploadId, settings), partNumber);
            } catch (IOException e) {
              LOG.infoAndDebugDetails(() -> "Got exception while trying to generate presigned url for part: " + e.getMessage(), e);
              throw new RuntimeException(e);
            }
          }).collect(Collectors.toList());
          return PresignedUrlDto.multiPart(request.objectKey, uploadId, presignedUrls);
        } else {
          return PresignedUrlDto.singlePart(request.objectKey, myPreSignedManager.generateUploadUrl(request.objectKey, settings));
        }
      } catch (Exception e) {
        LOG.infoAndDebugDetails(() -> "Got exception while trying to generate presigned url: " + e.getMessage(), e);
        throw new RuntimeException(e);
      }
    }).collect(Collectors.toList());
    return serializeResponseV2(PresignedUrlListResponseDto.createV2(responses));
  }

  @NotNull
  private String presignedUrlsV1(@NotNull final PresignedUrlListRequestDto requests,
                                 @NotNull final S3PreSignedManager.S3Settings settings) {
    return serializeResponseV1(PresignedUrlListResponseDto.createV1(requests.presignedUrlRequests.stream().map(request -> {
      try {
        return PresignedUrlDto.singlePart(request.objectKey, myPreSignedManager.generateUploadUrl(request.objectKey, settings));
      } catch (IOException e) {
        LOG.infoAndDebugDetails("Got exception while generating presigned URL: " + e.getMessage(), e);
        throw new RuntimeException(e);
      }
    }).collect(Collectors.toList())));
  }

  private void finishMultipartUpload(@NotNull final HttpServletRequest httpServletRequest,
                                     @NotNull final S3PreSignedManager.S3Settings settings) throws Exception {
    final String objectKey = httpServletRequest.getParameter(OBJECT_KEY);
    if (StringUtil.isEmpty(objectKey)) {
      throw new HttpServerErrorException(HttpStatus.BAD_REQUEST, OBJECT_KEY + " should be present");
    }
    final String uploadId = httpServletRequest.getParameter(FINISH_UPLOAD);
    if (StringUtil.isEmpty(uploadId)) {
      throw new HttpServerErrorException(HttpStatus.BAD_REQUEST, FINISH_UPLOAD + " should be present");
    }
    final boolean isSuccessful = Boolean.parseBoolean(httpServletRequest.getParameter(UPLOAD_SUCCESSFUL));
    final String[] eTags = httpServletRequest.getParameterValues(ETAGS);
    if (isSuccessful && (eTags == null || eTags.length < 1)) {
      throw new HttpServerErrorException(HttpStatus.BAD_REQUEST, ETAGS + " should be present");
    }
    myPreSignedManager.finishMultipartUpload(uploadId, objectKey, settings, eTags, isSuccessful);
  }

  @Nullable
  private RunningBuildEx getRunningBuild(@NotNull final HttpServletRequest request) {
    AuthorizationHeader header = AuthorizationHeader.getFrom(request);
    if (header != null) {
      SimpleCredentials cre = header.getBasicAuthCredentials();
      if (cre != null) {
        long buildId = BuildAuthUtil.getBuildId(cre.getUsername());
        if (buildId == -1) return null;
        return myRunningBuildsManager.findRunningBuildById(buildId);
      }
    }
    return null;
  }

  private enum RequestType {
    FINISH_MULTIPART_UPLOAD,
    GENERATE_PRESIGNED_URLS;

    @NotNull
    public static RequestType fromRequest(@NotNull final HttpServletRequest request) {
      return StringUtil.isNotEmpty(request.getParameter(FINISH_UPLOAD)) ? FINISH_MULTIPART_UPLOAD : GENERATE_PRESIGNED_URLS;
    }
  }
}
