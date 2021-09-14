package jetbrains.buildServer.artifacts.s3.cleanup;

public interface CleanupListener {
  void onError(Exception exception, boolean isRecoverable);
  void onSuccess(String objectKey);
}
