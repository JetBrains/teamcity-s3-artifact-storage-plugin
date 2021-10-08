package jetbrains.buildServer.artifacts.s3.publish.errors;

import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.HttpClientUtil;
import jetbrains.buildServer.transport.AgentServerSharedErrorMessages;
import org.jetbrains.annotations.NotNull;

public class TeamCityPresignedUrlsProviderErrorHandler implements HttpResponseErrorHandler {
  @Override
  public boolean canHandle(@NotNull ResponseAdapter responseWrapper) {
    final String header = responseWrapper.getHeader(S3Constants.ERROR_SOURCE_HEADER_NAME);
    return header != null && (S3Constants.ErrorSource.TEAMCITY.name().equals(header) || S3Constants.ErrorSource.SDK.name().equals(header));
  }

  @NotNull
  @Override
  public HttpClientUtil.HttpErrorCodeException handle(@NotNull ResponseAdapter responseWrapper) {
    final String response = responseWrapper.getResponse();
    if (response != null) {
      return new HttpClientUtil.HttpErrorCodeException(responseWrapper.getStatusCode(), response, false,
                                                       response.contains(AgentServerSharedErrorMessages.buildIsAlreadyFinishedOrDoesNotExist()));
    } else {
      return new HttpClientUtil.HttpErrorCodeException(responseWrapper.getStatusCode(), null, OUR_RECOVERABLE_STATUS_CODES.contains(responseWrapper.getStatusCode()));
    }
  }
}
