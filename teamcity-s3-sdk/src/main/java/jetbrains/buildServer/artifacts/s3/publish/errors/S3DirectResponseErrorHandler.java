package jetbrains.buildServer.artifacts.s3.publish.errors;

import jetbrains.buildServer.artifacts.s3.publish.presigned.util.HttpClientUtil;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.S3ErrorDto;
import jetbrains.buildServer.artifacts.s3.serialization.S3XmlSerializerFactory;
import org.jetbrains.annotations.NotNull;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.retry.RetryUtils;

/**
 * Error handler for handling S3 errors that are coming from S3 itself and not TeamCity server
 */
public class S3DirectResponseErrorHandler implements HttpResponseErrorHandler {
  @Override
  public boolean canHandle(@NotNull ResponseAdapter responseWrapper) {
    return responseWrapper.getHeader("x-amz-request-id") != null;
  }

  @NotNull
  @Override
  public HttpClientUtil.HttpErrorCodeException handle(@NotNull ResponseAdapter responseWrapper) {
    if (responseWrapper.getResponse() != null) {
      final S3ErrorDto deserialize = S3XmlSerializerFactory.getInstance().deserialize(responseWrapper.getResponse(), S3ErrorDto.class);
      deserialize.setCode(String.valueOf(responseWrapper.getStatusCode()));
      final AwsServiceException exception = deserialize.toException();
      final boolean isRequestExpired = exception.statusCode() == 403 && "Request has expired".equals(exception.awsErrorDetails().errorMessage());
      final boolean isRecoverable = isRequestExpired || RetryUtils.isRetryableException(exception) || RetryUtils.isThrottlingException(exception);
      return new HttpClientUtil.HttpErrorCodeException(exception.statusCode(), exception.getMessage(), isRecoverable);
    } else {
      return new HttpClientUtil.HttpErrorCodeException(responseWrapper.getStatusCode(), null, false);
    }
  }
}
