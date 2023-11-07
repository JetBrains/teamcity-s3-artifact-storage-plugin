package jetbrains.buildServer.artifacts.s3.serialization;

import jetbrains.buildServer.artifacts.s3.serialization.impl.XmlSerializerImpl;
import jetbrains.buildServer.util.impl.Lazy;
import org.jetbrains.annotations.NotNull;

public final class S3XmlSerializerFactory {
  @NotNull
  private static final Lazy<XmlSerializerImpl> OUR_SERIALIZER = new Lazy<XmlSerializerImpl>() {
    @NotNull
    @Override
    protected XmlSerializerImpl createValue() {
      return new XmlSerializerImpl();
    }
  };

  private S3XmlSerializerFactory() {
  }

  @NotNull
  public static XmlSerializer getInstance() {
    return OUR_SERIALIZER.get();
  }

}
