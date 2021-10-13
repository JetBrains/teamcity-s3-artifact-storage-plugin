package jetbrains.buildServer.artifacts.s3.transfer.model;

import java.util.Objects;

/**
 * Represents a name-value-type relation.
 */
public class Property {
  private String name;
  private String value;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Property property = (Property)o;
    return Objects.equals(name, property.name) && Objects.equals(value, property.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, value);
  }

  @Override
  public String toString() {
    return "Property{" +
           "name='" + name + '\'' +
           ", value='" + value + '\'' +
           '}';
  }
}

