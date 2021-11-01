package jetbrains.buildServer.artifacts.s3.transfer.model;

import java.util.HashMap;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public class Feature {

  @NotNull
  private final String myId;
  @NotNull
  private final String myType;
  @NotNull
  private final HashMap<String, String> myProperties;

  public Feature(@NotNull String id, @NotNull String type, @NotNull HashMap<String, String> properties) {

    myId = id;
    myType = type;
    myProperties = properties;
  }

  @NotNull
  public String getId() {
    return myId;
  }

  @NotNull
  public String getType() {
    return myType;
  }

  @NotNull
  public HashMap<String, String> getProperties() {
    return myProperties;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Feature feature = (Feature)o;
    return Objects.equals(myId, feature.myId) && Objects.equals(myType, feature.myType) && Objects.equals(myProperties, feature.myProperties);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myId, myType, myProperties);
  }
}

