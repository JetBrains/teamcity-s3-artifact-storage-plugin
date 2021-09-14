package jetbrains.buildServer.artifacts.s3.cleanup;

public class AbstractCleanupListener implements CleanupListener{
  @Override
  public void onError(Exception exception, boolean isRecoverable) {
  }

  @Override
  public void onSuccess(String objectKey) {
  }
}
