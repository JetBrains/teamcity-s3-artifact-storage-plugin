package jetbrains.buildServer.artifacts.s3.publish.presigned.util;

import com.amazonaws.AmazonServiceException;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import org.jetbrains.annotations.NotNull;

@XmlRootElement(name = "error")
public class S3ErrorDto {
  @XmlElement(name = "Code")
  private String code;
  @XmlElement(name = "Message")
  private String message;
  @XmlElement(name = "RequestId")
  private String requestId;
  @XmlElement(name = "HostId")
  private String hostId;

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
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

  @NotNull
  public AmazonServiceException toException() {
    AmazonServiceException exception = new AmazonServiceException(message);
    exception.setErrorCode(code);
    exception.setProxyHost(hostId);
    exception.setRequestId(requestId);
    return exception;
  }
}
