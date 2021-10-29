package jetbrains.buildServer.artifacts.s3.transport;

import com.amazonaws.AmazonServiceException;
import javax.xml.bind.annotation.XmlRootElement;
import org.jetbrains.annotations.NotNull;

@XmlRootElement(name = "error")
public class AmazonServiceErrorDto {
  private String error;
  private String code;
  private String requestId;
  private String hostId;
  private int statusCode;

  @NotNull
  public static AmazonServiceErrorDto from(@NotNull final AmazonServiceException e) {
    final AmazonServiceErrorDto errorDto = new AmazonServiceErrorDto();
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
  public AmazonServiceException toException() {
    AmazonServiceException exception = new AmazonServiceException(error);
    exception.setErrorCode(code);
    exception.setProxyHost(hostId);
    exception.setRequestId(requestId);
    exception.setStatusCode(statusCode);
    return exception;
  }
}
