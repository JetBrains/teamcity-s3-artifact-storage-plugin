package jetbrains.buildServer.artifacts.s3.transfer;

public class Parameters {
  private final String token;
  private final String target;
  private final String source;

  public Parameters(String source, String target, String token) {
    this.token = token;
    this.target = target;
    this.source = source;
  }

  public String getToken() {
    return token;
  }

  public String getTarget() {
    return target;
  }

  public String getSource() {
    return source;
  }

}
