package jetbrains.buildServer.artifacts.s3.publish.errors;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.retry.RetryUtils;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.HttpClientUtil;
import jetbrains.buildServer.artifacts.s3.serialization.S3XmlSerializerFactory;
import jetbrains.buildServer.artifacts.s3.transport.AmazonServiceErrorDto;
import org.jetbrains.annotations.NotNull;

public class S3ServerResponseErrorHandler implements HttpResponseErrorHandler {
  @Override
  public boolean canHandle(@NotNull ResponseAdapter responseWrapper) {
    return S3Constants.ErrorSource.S3.name().equals(responseWrapper.getHeader(S3Constants.ERROR_SOURCE_HEADER_NAME));
  }

  @NotNull
  @Override
  public HttpClientUtil.HttpErrorCodeException handle(@NotNull ResponseAdapter responseWrapper) {
    if (OUR_RECOVERABLE_STATUS_CODES.contains(responseWrapper.getStatusCode())) {
      return new HttpClientUtil.HttpErrorCodeException(responseWrapper.getStatusCode(), responseWrapper.getResponse(), true);
    }
    if (responseWrapper.getResponse() != null) {
      final AmazonServiceErrorDto deserialize = S3XmlSerializerFactory.getInstance().deserialize(responseWrapper.getResponse(), AmazonServiceErrorDto.class);
      final AmazonServiceException exception = deserialize.toException();
      final boolean isRecoverable = RetryUtils.isRetryableServiceException(exception) || RetryUtils.isThrottlingException(exception);
      return new HttpClientUtil.HttpErrorCodeException(exception.getStatusCode(), exception.getMessage(), isRecoverable);
    } else {
      return new HttpClientUtil.HttpErrorCodeException(responseWrapper.getStatusCode(), null, false);
    }
  }
}
