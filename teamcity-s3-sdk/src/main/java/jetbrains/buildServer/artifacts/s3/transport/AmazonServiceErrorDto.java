package jetbrains.buildServer.artifacts.s3.transport;

import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
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
  public static AmazonServiceErrorDto from(@NotNull final AwsServiceException e) {
    final AmazonServiceErrorDto errorDto = new AmazonServiceErrorDto();
    final AwsErrorDetails details = e.awsErrorDetails();
    if (details != null) {
      errorDto.error = details.errorMessage();
      errorDto.code = details.errorCode();
    }
    errorDto.hostId = e.extendedRequestId();
    errorDto.requestId = e.requestId();
    errorDto.statusCode = e.statusCode();
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
  public AwsServiceException toException() {
    AwsErrorDetails details = AwsErrorDetails.builder()
                                             .errorMessage(error)
                                             .errorCode(code)
                                             .build();
    return AwsServiceException.builder()
                              .awsErrorDetails(details)
                              .requestId(requestId)
                              .extendedRequestId(hostId)
                              .statusCode(statusCode)
                              .build();
  }
}
