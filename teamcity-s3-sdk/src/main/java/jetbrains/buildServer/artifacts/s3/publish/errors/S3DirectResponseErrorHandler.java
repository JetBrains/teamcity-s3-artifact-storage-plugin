package jetbrains.buildServer.artifacts.s3.publish.errors;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.retry.RetryUtils;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.HttpClientUtil;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.S3ErrorDto;
import jetbrains.buildServer.artifacts.s3.serialization.S3XmlSerializerFactory;
import org.jetbrains.annotations.NotNull;

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
      final AmazonServiceException exception = deserialize.toException();
      exception.setStatusCode(responseWrapper.getStatusCode());
      return new HttpClientUtil.HttpErrorCodeException(exception.getStatusCode(), exception.getMessage(), RetryUtils.isRetryableServiceException(exception));
    } else {
      return new HttpClientUtil.HttpErrorCodeException(responseWrapper.getStatusCode(), null, false);
    }
  }
}
