package jetbrains.buildServer.artifacts.s3.publish.errors;

import com.amazonaws.retry.RetryUtils;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import jetbrains.buildServer.artifacts.s3.S3Constants;
import jetbrains.buildServer.artifacts.s3.publish.presigned.util.HttpClientUtil;
import jetbrains.buildServer.artifacts.s3.serialization.S3XmlSerializerFactory;
import jetbrains.buildServer.artifacts.s3.transport.AmazonS3ErrorDto;
import org.jetbrains.annotations.NotNull;

public class S3ResponseErrorHandler implements HttpResponseErrorHandler {
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
      final AmazonS3ErrorDto deserialize = S3XmlSerializerFactory.getInstance().deserialize(responseWrapper.getResponse(), AmazonS3ErrorDto.class);
      final AmazonS3Exception exception = deserialize.toException();
      return new HttpClientUtil.HttpErrorCodeException(exception.getStatusCode(), exception.getMessage(), RetryUtils.isRetryableServiceException(exception));
    } else {
      return new HttpClientUtil.HttpErrorCodeException(responseWrapper.getStatusCode(), null, false);
    }
  }
}
