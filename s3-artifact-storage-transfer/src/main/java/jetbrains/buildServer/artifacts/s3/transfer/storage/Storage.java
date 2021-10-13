package jetbrains.buildServer.artifacts.s3.transfer.storage;

import java.io.File;
import jetbrains.buildServer.artifacts.s3.transfer.model.Build;

public interface Storage {
  String getType();

  File download(String artifact, Build Build);

  void upload(File artifact, Build metadata);

  void delete(File artifact);

  String getFeatureId();
}
