package jetbrains.buildServer.artifacts.s3.exceptions;

import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

public class InvalidSettingsException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  @NotNull
  private final Map<String, String> myInvalids;

  public InvalidSettingsException(@NotNull Map<String, String> invalids) {
    myInvalids = new HashMap<>(invalids);
  }

  @Override
  public String getMessage() {
    return StringUtil.join("\n", myInvalids.values());
  }

  @NotNull
  public Map<String, String> getInvalids() {
    return myInvalids;
  }

}
