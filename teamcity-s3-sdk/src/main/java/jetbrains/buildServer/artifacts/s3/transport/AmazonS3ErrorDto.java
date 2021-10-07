package jetbrains.buildServer.artifacts.s3.transport;

import com.amazonaws.services.s3.internal.AmazonS3ExceptionBuilder;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import javax.xml.bind.annotation.XmlRootElement;
import org.jetbrains.annotations.NotNull;

@XmlRootElement(name = "error")
public class AmazonS3ErrorDto {
  private String error;
  private String code;
  private String requestId;
  private String hostId;
  private int statusCode;

  @NotNull
  public static AmazonS3ErrorDto from(@NotNull final AmazonS3Exception e) {
    final AmazonS3ErrorDto errorDto = new AmazonS3ErrorDto();
    errorDto.error = e.getErrorMessage();
    errorDto.code = e.getErrorCode();
    errorDto.hostId = e.getProxyHost();
    errorDto.requestId = e.getRequestId();
    errorDto.statusCode = e.getStatusCode();
    return errorDto;
  }

  public String getError() {
    return error;
  }

  public void setError(String error) {
    this.error = error;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getRequestId() {
    return requestId;
  }

  public void setRequestId(String requestId) {
    this.requestId = requestId;
  }

  public String getHostId() {
    return hostId;
  }

  public void setHostId(String hostId) {
    this.hostId = hostId;
  }

  public int getStatusCode() {
    return statusCode;
  }

  public void setStatusCode(int statusCode) {
    this.statusCode = statusCode;
  }

  @NotNull
  public AmazonS3Exception toException() {
    final AmazonS3ExceptionBuilder builder = new AmazonS3ExceptionBuilder();
    builder.setErrorMessage(error);
    builder.setErrorCode(code);
    builder.setProxyHost(hostId);
    builder.setRequestId(requestId);
    builder.setStatusCode(statusCode);
    return builder.build();
  }
}
