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

package jetbrains.buildServer.artifacts.s3.web;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.HttpMethod;
import com.amazonaws.SdkBaseException;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.StreamUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.BuildAuthUtil;
import jetbrains.buildServer.artifacts.ServerArtifactStorageSettingsProvider;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.S3Util;
import jetbrains.buildServer.artifacts.s3.exceptions.InvalidSettingsException;
import jetbrains.buildServer.artifacts.s3.serialization.S3XmlSerializerFactory;
import jetbrains.buildServer.artifacts.s3.transport.*;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.controllers.interceptors.auth.util.AuthorizationHeader;
import jetbrains.buildServer.filestorage.cloudfront.CloudFrontEnabledPresignedUrlProvider;
import jetbrains.buildServer.filestorage.cloudfront.CloudFrontSettings;
import jetbrains.buildServer.filestorage.cloudfront.RequestMetadata;
import jetbrains.buildServer.http.SimpleCredentials;
import jetbrains.buildServer.serverSide.ProjectManagerEx;
import jetbrains.buildServer.serverSide.RunningBuildEx;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.serverSide.impl.RunningBuildsManagerEx;
import jetbrains.buildServer.util.ExceptionUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.Util;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import jetbrains.buildServer.web.util.WebUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.servlet.ModelAndView;

import static java.util.Base64.getDecoder;
import static jetbrains.buildServer.artifacts.s3.S3Constants.ARTEFACTS_S3_UPLOAD_PRESIGN_URLS_HTML;
import static jetbrains.buildServer.artifacts.s3.S3Constants.ERROR_SOURCE_HEADER_NAME;
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
  private final CloudFrontEnabledPresignedUrlProvider myPreSignedManager;
  @NotNull
  private final ServerArtifactStorageSettingsProvider myStorageSettingsProvider;
  @NotNull
  private final ProjectManagerEx myProjectManager;

  public S3PreSignedUrlController(@NotNull WebControllerManager web,
                                  @NotNull RunningBuildsManagerEx runningBuildsManager,
                                  @NotNull CloudFrontEnabledPresignedUrlProvider preSignedManager,
                                  @NotNull ServerArtifactStorageSettingsProvider storageSettingsProvider,
                                  @NotNull ProjectManagerEx projectManager) {
    myRunningBuildsManager = runningBuildsManager;
    myPreSignedManager = preSignedManager;
    myStorageSettingsProvider = storageSettingsProvider;
    myProjectManager = projectManager;
    web.registerController(ARTEFACTS_S3_UPLOAD_PRESIGN_URLS_HTML, this);
  }

  @SuppressWarnings("MagicConstant")
  @Nullable
  @Override
  protected ModelAndView doHandle(@NotNull HttpServletRequest httpServletRequest, @NotNull HttpServletResponse httpServletResponse) throws Exception {
    try {
      final Pair<RequestType, CloudFrontSettings> request = parseRequest(httpServletRequest);

      httpServletResponse.setContentType("application/xml; charset=" + StandardCharsets.UTF_8.name());
      if (request.getFirst() == RequestType.FINISH_MULTIPART_UPLOAD) {
        finishMultipartUpload(httpServletRequest, request.getSecond());
        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
      } else {
        final String response = providePresignedUrls(httpServletRequest, request.getSecond());
        httpServletResponse.getWriter().append(response);
        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
      }
      return null;
    } catch (final Exception e) {
      logError(httpServletRequest, e);
      handleException(httpServletResponse, e);
      return null;
    }
  }

  private void handleException(@NotNull final HttpServletResponse httpServletResponse, @NotNull final Exception e) throws IOException {
    final Exception cause = getMostInformativeRootException(e);
    setErrorHeader(httpServletResponse, cause);
    if (cause instanceof AmazonServiceException) {
      handleAmazonException(httpServletResponse, (AmazonServiceException)cause);
    } else {
      handleGenericException(httpServletResponse, cause);
    }
  }

  @NotNull
  private Exception getMostInformativeRootException(@NotNull final Exception e) {
    return Util.ofNullable(() -> ExceptionUtil.getCause(e, AmazonS3Exception.class),
                           Util.ofNullable(() -> ExceptionUtil.getCause(e, SdkBaseException.class),
                                           Util.ofNullable(() -> ExceptionUtil.getCause(e, HttpStatusCodeException.class), e)));
  }

  private void setErrorHeader(HttpServletResponse httpServletResponse, Exception e) {
    final String header;
    if (e instanceof AmazonServiceException) {
      header = S3Constants.ErrorSource.S3.name();
    } else if (e instanceof SdkBaseException) {
      header = S3Constants.ErrorSource.SDK.name();
    } else {
      header = S3Constants.ErrorSource.TEAMCITY.name();
    }
    httpServletResponse.setHeader(ERROR_SOURCE_HEADER_NAME, header);
  }

  private void handleGenericException(@NotNull final HttpServletResponse response, @NotNull final Exception e) throws IOException {
    response.sendError(e instanceof HttpStatusCodeException ? ((HttpStatusCodeException)e).getRawStatusCode() : HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
  }

  private void handleAmazonException(@NotNull HttpServletResponse httpServletResponse, AmazonServiceException e) throws IOException {
    httpServletResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    httpServletResponse.getWriter().append(S3XmlSerializerFactory.getInstance().serialize(AmazonServiceErrorDto.from(e)));
  }

  private void logError(@NotNull final HttpServletRequest request,
                        @NotNull final Exception e) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Request '" + WebUtil.getRequestDump(request) + "' failed with error '" + e.getMessage() + "'", e);
    } else {
      LOG.warnAndDebugDetails("Request '" + WebUtil.getShortRequestDescription(request) + "' failed with error '" + e.getMessage() + "'", e);
    }
  }

  @NotNull
  private Pair<RequestType, CloudFrontSettings> parseRequest(@NotNull final HttpServletRequest request) {
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
    String requestRegion = request.getHeader(S3Constants.S3_REGION_HEADER_NAME);
    String userAgent = WebUtil.getUserAgent(request);

    Map<String, String> allSettings = new HashMap<>();
    final ProjectEx project = myProjectManager.findProjectById(runningBuild.getProjectId());
    if (project != null) {
      allSettings.putAll(project.getParameters());
    }
    allSettings.putAll(storageSettings);

    return Pair.create(RequestType.fromRequest(request), myPreSignedManager.settings(allSettings, RequestMetadata.from(requestRegion, userAgent)));
  }

  @NotNull
  private String providePresignedUrls(@NotNull final HttpServletRequest httpServletRequest,
                                      @NotNull final CloudFrontSettings settings) throws Exception {
    final PresignedUrlListRequestDto request = PresignedUrlRequestSerializer.deserializeRequest(StreamUtil.readTextFrom(httpServletRequest.getReader()));
    return request.isVersion2()
           ? presignedUrlsV2(request, settings)
           : presignedUrlsV1(request, settings);
  }

  @NotNull
  private String presignedUrlsV2(@NotNull final PresignedUrlListRequestDto requestList,
                                 @NotNull final CloudFrontSettings settings) {
    final List<PresignedUrlDto> responses = requestList.getPresignedUrlRequests().stream().map(request -> {
      try {
        if (request.getDigests() != null && request.getDigests().size() > 1) {
          final String uploadId = myPreSignedManager.startMultipartUpload(request.getObjectKey(), settings);
          final List<PresignedUrlPartDto> presignedUrls = new ArrayList<>();
          for (int i = 0; i < request.getDigests().size(); i++) {
            final String digest = request.getDigests().get(i);
            int partNumber = i + 1;
            try {
              final String url = myPreSignedManager.generateUploadUrlForPart(request.getObjectKey(), digest, partNumber, uploadId, settings);
              presignedUrls.add(new PresignedUrlPartDto(url, partNumber));
            } catch (IOException e) {
              LOG.infoAndDebugDetails(() -> "Got exception while trying to generate presigned url for part: " + e.getMessage(), e);
              throw new RuntimeException(e);
            }
          }
          return PresignedUrlDto.multiPart(request.getObjectKey(), uploadId, presignedUrls);
        } else if (request.getNumberOfParts() > 1) {
          final String uploadId = myPreSignedManager.startMultipartUpload(request.getObjectKey(), settings);
          final List<PresignedUrlPartDto> presignedUrls = IntStream.rangeClosed(1, request.getNumberOfParts()).mapToObj(partNumber -> {
            try {
              return new PresignedUrlPartDto(myPreSignedManager.generateUploadUrlForPart(request.getObjectKey(), null, partNumber, uploadId, settings), partNumber);
            } catch (IOException e) {
              LOG.infoAndDebugDetails(() -> "Got exception while trying to generate presigned url for part: " + e.getMessage(), e);
              throw new RuntimeException(e);
            }
          }).collect(Collectors.toList());
          return PresignedUrlDto.multiPart(request.getObjectKey(), uploadId, presignedUrls);
        } else if (request.getDigests() != null && request.getDigests().size() == 1) {
          return PresignedUrlDto.singlePart(request.getObjectKey(), myPreSignedManager.generateUploadUrl(request.getObjectKey(), request.getDigests().get(0), settings));
        } else if (request.getHttpMethod() != null) {
          return PresignedUrlDto.singlePart(request.getObjectKey(),
                                            myPreSignedManager.generateDownloadUrl(HttpMethod.valueOf(request.getHttpMethod()), request.getObjectKey(), settings));
        } else {
          return PresignedUrlDto.singlePart(request.getObjectKey(), myPreSignedManager.generateUploadUrl(request.getObjectKey(), null, settings));
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
                                 @NotNull final CloudFrontSettings settings) {
    return serializeResponseV1(PresignedUrlListResponseDto.createV1(requests.getPresignedUrlRequests().stream().map(request -> {
      try {
        return PresignedUrlDto.singlePart(request.getObjectKey(), myPreSignedManager.generateUploadUrl(request.getObjectKey(), null, settings));
      } catch (IOException e) {
        LOG.infoAndDebugDetails("Got exception while generating presigned URL: " + e.getMessage(), e);
        throw new RuntimeException(e);
      }
    }).collect(Collectors.toList())));
  }

  private void finishMultipartUpload(@NotNull final HttpServletRequest httpServletRequest,
                                     @NotNull final CloudFrontSettings settings) throws Exception {
    final String objectKeyBase64 = new String(getDecoder().decode(StringUtil.emptyIfNull(httpServletRequest.getParameter(OBJECT_KEY + "_BASE64"))), StandardCharsets.UTF_8);
    final String objectKey = StringUtil.isNotEmpty(objectKeyBase64) ? objectKeyBase64 : httpServletRequest.getParameter(OBJECT_KEY);
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
